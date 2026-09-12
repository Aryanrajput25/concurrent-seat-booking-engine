-- The authoritative ownership table: one row per (show, seat), locked with
-- SELECT ... FOR UPDATE by the application on every state transition. This
-- is what actually decides who owns a seat — Redis is only a fast,
-- short-lived pre-check in front of it.

CREATE TABLE show_seat_reservations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id    BIGINT NOT NULL,
    seat_id    BIGINT NOT NULL,
    booking_id BIGINT NULL,
    state      VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_reservation_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_reservation_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    CONSTRAINT fk_reservation_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uq_show_seat_reservation UNIQUE (show_id, seat_id)
);
