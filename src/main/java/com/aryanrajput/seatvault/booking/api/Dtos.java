package com.aryanrajput.seatvault.booking.api;

import com.aryanrajput.seatvault.booking.domain.BookingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

/**
 * Request and response shapes for the REST API. Kept as plain records in one
 * file, deliberately separate from the JPA entities in {@code domain} —
 * entities never cross the HTTP boundary directly.
 */
public final class Dtos {

    private Dtos() {
        // namespace only
    }

    public record IdResponse(Long id) {
    }

    public record NameRequest(@NotBlank String name) {
    }

    public record SeatRequest(@Positive int rowNumber, @Positive int seatNumber) {
    }

    public record CreateShowRequest(
            @NotNull Long movieId,
            @NotNull Long screenId,
            @NotNull Instant startTime,
            @Positive int durationMinutes) {
    }

    public record CreateBookingRequest(
            @NotBlank String userId,
            @NotNull Long showId,
            @NotEmpty List<@NotNull Long> seatIds) {
    }

    public record UserRequest(@NotBlank String userId) {
    }

    public record SeatResponse(Long id, int rowNumber, int seatNumber, boolean available) {
    }

    public record AvailableSeatsResponse(Long showId, List<SeatResponse> seats) {
    }

    public record BookingResponse(
            Long id,
            Long showId,
            String userId,
            BookingStatus status,
            Instant createdAt,
            int paymentFailures,
            List<Long> seatIds) {
    }

    public record ShowResponse(Long id, Long movieId, Long screenId, Instant startsAt, Instant endsAt) {
    }
}
