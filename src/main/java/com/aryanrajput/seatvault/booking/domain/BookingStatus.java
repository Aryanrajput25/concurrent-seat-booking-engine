package com.aryanrajput.seatvault.booking.domain;

/**
 * Lifecycle states for a {@link Booking}.
 *
 * <pre>
 *   PENDING --(payment success)--&gt; CONFIRMED
 *   PENDING --(payment failed too many times, or hold timeout)--&gt; EXPIRED
 *   PENDING --(user cancels)--&gt; CANCELLED
 * </pre>
 *
 * PENDING is the only state from which a booking can still change; the other
 * three are terminal.
 */
public enum BookingStatus { //This represents the booking lifecycle.
    PENDING,
    CONFIRMED,
    EXPIRED,
    CANCELLED
}
