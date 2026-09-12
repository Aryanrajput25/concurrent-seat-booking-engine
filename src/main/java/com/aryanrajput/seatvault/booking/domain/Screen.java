package com.aryanrajput.seatvault.booking.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;

/**
 * A single auditorium inside a {@link Theatre}. A screen owns a fixed grid of
 * {@link Seat}s and hosts a schedule of {@link Show}s over time.
 */
@Entity
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    private Theatre theatre;

    protected Screen() {
        // required by JPA
    }

    public Screen(String name, Theatre theatre) {
        this.name = name;
        this.theatre = theatre;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Theatre getTheatre() {
        return theatre;
    }
}
