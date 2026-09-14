package com.aryanrajput.seatvault.booking.domain;

/**
 * State of a single seat, for a single show, as tracked by
 * {@link ShowSeatReservation}.
 *
 * <ul>
 *   <li>{@code AVAILABLE} — free to be claimed by a new booking.</li>
 *   <li>{@code HELD} — claimed by a pending booking, with an expiry time.
 *       Still counts as unavailable until that time passes.</li>
 *   <li>{@code CONFIRMED} — payment succeeded; permanently unavailable.</li>
 * </ul>
 */
public enum ReservationState { //This represents the state of a reservation.
    AVAILABLE,
    HELD,
    CONFIRMED
}
