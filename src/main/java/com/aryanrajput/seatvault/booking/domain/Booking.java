package com.aryanrajput.seatvault.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A user's attempt to book one or more seats for a {@link Show}.
 *
 * <p>A booking starts life as {@code PENDING}. Whether it becomes
 * {@code CONFIRMED}, {@code EXPIRED}, or {@code CANCELLED} is decided by
 * {@link com.aryanrajput.seatvault.booking.service.BookingService}, which is also
 * responsible for enforcing valid state transitions — this entity just
 * stores the result of that decision.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Show show;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private int paymentFailures;

    @ManyToMany
    @JoinTable(
            name = "booking_seats",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "seat_id")
    )
    private List<Seat> seats = new ArrayList<>();

    protected Booking() {
        // required by JPA
    }

    public Booking(Show show, String userId, List<Seat> seats) {
        this.show = show;
        this.userId = userId;
        this.seats.addAll(seats);
    }

    public Long getId() {
        return id;
    }

    public Show getShow() {
        return show;
    }

    public String getUserId() {
        return userId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public int getPaymentFailures() {
        return paymentFailures;
    }

    public void confirm() {
        status = BookingStatus.CONFIRMED;
    }

    public void paymentFailed() {
        paymentFailures++;
    }

    public void expire() {
        status = BookingStatus.EXPIRED;
    }

    public void cancel() {
        status = BookingStatus.CANCELLED;
    }
}
