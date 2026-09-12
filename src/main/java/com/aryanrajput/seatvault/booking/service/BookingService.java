package com.aryanrajput.seatvault.booking.service;

import com.aryanrajput.seatvault.booking.domain.Booking;
import com.aryanrajput.seatvault.booking.domain.BookingStatus;
import com.aryanrajput.seatvault.booking.domain.ConfirmedShowSeat;
import com.aryanrajput.seatvault.booking.domain.ReservationState;
import com.aryanrajput.seatvault.booking.domain.Seat;
import com.aryanrajput.seatvault.booking.domain.Show;
import com.aryanrajput.seatvault.booking.domain.ShowSeatReservation;
import com.aryanrajput.seatvault.booking.repo.Repositories.Bookings;
import com.aryanrajput.seatvault.booking.repo.Repositories.ConfirmedSeats;
import com.aryanrajput.seatvault.booking.repo.Repositories.Seats;
import com.aryanrajput.seatvault.booking.repo.Repositories.Shows;
import com.aryanrajput.seatvault.booking.repo.Repositories.ShowSeatReservations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Owns every state transition a {@link Booking} can go through: creation,
 * payment success, payment failure, cancellation, and background expiry.
 *
 * <p><b>How correctness under concurrency works here:</b> {@link SeatLockService}
 * (Redis) is checked first and rejects most contention immediately. But the
 * transition that actually matters — moving a {@code ShowSeatReservation}
 * from {@code AVAILABLE}/{@code HELD} to a new owner — always happens after
 * locking that row with {@code SELECT ... FOR UPDATE} (see
 * {@link ShowSeatReservations#lockAll}). That row lock is what makes MySQL,
 * not Redis, the final word on who owns a seat: a booking whose Redis lock
 * has expired can never confirm, because by the time it re-reads its
 * reservation row under lock, it will see it no longer holds a live hold.
 */
@Service
public class BookingService {

    private final Bookings bookings;
    private final Shows shows;
    private final Seats seats;
    private final ConfirmedSeats confirmedSeats;
    private final ShowSeatReservations reservations;
    private final SeatLockService seatLocks;
    private final int allowedPaymentFailures;
    private final Duration holdTimeout;

    public BookingService(
            Bookings bookings,
            Shows shows,
            Seats seats,
            ConfirmedSeats confirmedSeats,
            ShowSeatReservations reservations,
            SeatLockService seatLocks,
            @Value("${booking.allowed-payment-failures}") int allowedPaymentFailures,
            @Value("${booking.lock-timeout-seconds}") long holdTimeoutSeconds) {
        this.bookings = bookings;
        this.shows = shows;
        this.seats = seats;
        this.confirmedSeats = confirmedSeats;
        this.reservations = reservations;
        this.seatLocks = seatLocks;
        this.allowedPaymentFailures = allowedPaymentFailures;
        this.holdTimeout = Duration.ofSeconds(holdTimeoutSeconds);
    }

    /**
     * Creates a new pending booking and attempts to claim every requested
     * seat. Fails fast via Redis if any seat is already locked; falls back to
     * the authoritative, row-locked check in MySQL before actually claiming
     * the seats.
     *
     * @throws IllegalArgumentException if the seat selection itself is invalid
     *         (empty, duplicated, or not all on the show's screen)
     * @throws IllegalStateException if any seat is currently unavailable
     */
    @Transactional
    public Booking create(String userId, Long showId, List<Long> seatIds) {
        validateSeatSelection(seatIds);

        Show show = find(shows, showId, "Show");
        List<Seat> selectedSeats = seats.findAllById(seatIds);
        validateSeatsBelongToShow(show, selectedSeats, seatIds);

        // saveAndFlush so the generated booking id is available immediately —
        // we need it as the "owner" value written into the Redis lock below.
        Booking booking = bookings.saveAndFlush(new Booking(show, userId, selectedSeats));

        List<Long> orderedSeatIds = seatIds.stream().sorted().toList();

        if (!seatLocks.lockAll(showId, orderedSeatIds, booking.getId())) {
            throw new IllegalStateException("At least one seat is temporarily unavailable");
        }

        List<ShowSeatReservation> reservationRows = reservations.lockAll(showId, orderedSeatIds);
        boolean anySeatUnavailable = reservationRows.size() != orderedSeatIds.size()
                || reservationRows.stream().anyMatch(row -> !row.canBeClaimed(Instant.now()));

        if (anySeatUnavailable) {
            seatLocks.release(showId, orderedSeatIds, booking.getId());
            throw new IllegalStateException("At least one seat is unavailable");
        }

        Instant expiresAt = Instant.now().plus(holdTimeout);
        reservationRows.forEach(row -> row.claim(booking, expiresAt));

        return booking;
    }

    /**
     * Confirms a booking after successful payment. Idempotent: calling this
     * again on an already-confirmed booking simply returns it unchanged.
     *
     * @throws IllegalStateException if the hold expired or was reassigned to
     *         another booking before payment completed
     */
    @Transactional
    public Booking succeed(Long bookingId, String userId) {
        Booking booking = lockBooking(bookingId);
        verifyOwner(booking, userId);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return booking;
        }
        requirePending(booking);

        List<Long> seatIds = orderedSeatIdsOf(booking);
        List<ShowSeatReservation> reservationRows = reservations.lockAll(booking.getShow().getId(), seatIds);

        Instant now = Instant.now();
        boolean holdIsStillValid = reservationRows.size() == seatIds.size()
                && reservationRows.stream().allMatch(row -> row.belongsTo(booking) && row.isLiveHold(now));

        if (!holdIsStillValid) {
            expire(booking, reservationRows);
            throw new IllegalStateException("Seat hold has expired or was replaced");
        }

        for (Seat seat : booking.getSeats()) {
            boolean alreadyConfirmedElsewhere =
                    confirmedSeats.existsByShowIdAndSeatId(booking.getShow().getId(), seat.getId());
            if (alreadyConfirmedElsewhere) {
                throw new IllegalStateException("Seat was already confirmed");
            }
            confirmedSeats.save(new ConfirmedShowSeat(booking.getShow(), seat, booking));
        }
        confirmedSeats.flush();

        reservationRows.forEach(ShowSeatReservation::confirm);
        booking.confirm();
        seatLocks.release(booking.getShow().getId(), seatIds, booking.getId());

        return booking;
    }

    /**
     * Records a failed payment attempt. Once the number of failures exceeds
     * {@code booking.allowed-payment-failures}, the booking and its held
     * seats are expired so someone else can book them.
     */
    @Transactional
    public Booking fail(Long bookingId, String userId) {
        Booking booking = lockBooking(bookingId);
        verifyOwner(booking, userId);
        requirePending(booking);

        booking.paymentFailed();
        if (booking.getPaymentFailures() > allowedPaymentFailures) {
            expire(booking, lockReservationRowsOf(booking));
        }

        return booking;
    }

    /** Cancels a pending booking, releasing its held seats immediately. */
    @Transactional
    public Booking cancel(Long bookingId, String userId) {
        Booking booking = lockBooking(bookingId);
        verifyOwner(booking, userId);
        requirePending(booking);

        for (ShowSeatReservation row : lockReservationRowsOf(booking)) {
            if (row.belongsTo(booking) && row.getState() == ReservationState.HELD) {
                row.release();
            }
        }
        booking.cancel();
        seatLocks.release(booking.getShow().getId(), orderedSeatIdsOf(booking), booking.getId());

        return booking;
    }

    /**
     * Background sweep that expires any booking still {@code PENDING} after
     * its hold window has passed, freeing the seats it was holding. Runs
     * every {@code booking.expiration-sweep-seconds} seconds.
     */
    @Scheduled(fixedDelayString = "${booking.expiration-sweep-seconds:15}000")
    @Transactional
    public void expireStaleBookings() {
        Instant cutoff = Instant.now().minus(holdTimeout);

        bookings.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff)
                .stream()
                .map(Booking::getId)
                .forEach(id -> {
                    Booking booking = lockBooking(id);
                    boolean stillStalePending = booking.getStatus() == BookingStatus.PENDING
                            && booking.getCreatedAt().isBefore(cutoff);
                    if (stillStalePending) {
                        expire(booking, lockReservationRowsOf(booking));
                    }
                });
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void expire(Booking booking, List<ShowSeatReservation> reservationRows) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            return;
        }
        reservationRows.stream()
                .filter(row -> row.belongsTo(booking) && row.getState() == ReservationState.HELD)
                .forEach(ShowSeatReservation::release);
        booking.expire();
        seatLocks.release(booking.getShow().getId(), orderedSeatIdsOf(booking), booking.getId());
    }

    private List<ShowSeatReservation> lockReservationRowsOf(Booking booking) {
        return reservations.lockAll(booking.getShow().getId(), orderedSeatIdsOf(booking));
    }

    private Booking lockBooking(Long bookingId) {
        return bookings.lockById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
    }

    private List<Long> orderedSeatIdsOf(Booking booking) {
        return booking.getSeats().stream().map(Seat::getId).sorted().toList();
    }

    private void validateSeatSelection(List<Long> seatIds) {
        boolean empty = seatIds == null || seatIds.isEmpty();
        boolean hasDuplicates = seatIds != null && new HashSet<>(seatIds).size() != seatIds.size();
        if (empty || hasDuplicates) {
            throw new IllegalArgumentException("Choose one or more distinct seats");
        }
    }

    private void validateSeatsBelongToShow(Show show, List<Seat> selectedSeats, List<Long> requestedSeatIds) {
        if (selectedSeats.size() != requestedSeatIds.size()) {
            throw new NoSuchElementException("One or more seats not found");
        }
        boolean allOnShowsScreen = selectedSeats.stream()
                .allMatch(seat -> show.getScreen().getId().equals(seat.getScreen().getId()));
        if (!allOnShowsScreen) {
            throw new IllegalArgumentException("Every seat must belong to this show's screen");
        }
    }

    private void requirePending(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is no longer cancellable or payable");
        }
    }

    private void verifyOwner(Booking booking, String userId) {
        if (!booking.getUserId().equals(userId)) {
            throw new SecurityException("Booking belongs to another user");
        }
    }

    private <T> T find(JpaRepository<T, Long> repository, Long id, String entityLabel) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException(entityLabel + " not found: " + id));
    }
}
