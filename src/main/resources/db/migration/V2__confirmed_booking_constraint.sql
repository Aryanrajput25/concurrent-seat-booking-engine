-- Final defense-in-depth guard against double-booking: the database itself
-- refuses to let two rows exist for the same (show_id, seat_id) pair, no
-- matter what the application logic does.

-- this Adds confirmed booking constraint. it creates Additional protection against duplicate confirmed seats.
CREATE TABLE confirmed_show_seats (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    show_id    BIGINT NOT NULL,
    seat_id    BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    CONSTRAINT fk_confirmed_show FOREIGN KEY (show_id) REFERENCES shows (id),
    CONSTRAINT fk_confirmed_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    CONSTRAINT fk_confirmed_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uq_confirmed_show_seat UNIQUE (show_id, seat_id)
);
