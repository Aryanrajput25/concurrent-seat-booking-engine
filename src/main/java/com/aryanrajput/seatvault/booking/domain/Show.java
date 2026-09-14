package com.aryanrajput.seatvault.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single screening of a {@link Movie} on a {@link Screen} at a fixed time.
 *
 * <p>The JPQL entity name is set to {@code MovieShow} instead of the default
 * {@code Show} because {@code SHOW} is a reserved word in MySQL; the table
 * itself is named {@code shows} for the same reason.
 */
@Entity(name = "MovieShow")
@Table(name = "shows")
public class Show { //A Show represents a particular movie playing at a particular time/screen. eg-Movie: Interstellar, Screen: Screen 1, Time: 6:00 PM

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Movie movie;

    @ManyToOne(optional = false)
    private Screen screen;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private int durationMinutes;

    protected Show() {
        // required by JPA
    }

    public Show(Movie movie, Screen screen, Instant startTime, int durationMinutes) {
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Instant getEndTime() {
        return startTime.plusSeconds(durationMinutes * 60L);
    }
}
