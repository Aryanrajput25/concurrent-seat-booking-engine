package com.aryanrajput.seatvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for SeatVault.
 *
 * {@code @EnableScheduling} turns on the background sweep in {@link
 * com.aryanrajput.seatvault.booking.service.BookingService} that expires seat holds
 * whose payment window has passed.
 */
@SpringBootApplication
@EnableScheduling
public class SeatVaultApplication { //This is the entry point of your Spring Boot application.

    public static void main(String[] args) {
        SpringApplication.run(SeatVaultApplication.class, args);
    }
}
// @SpringBootApplication is the main Spring Boot configuration annotation.
// It combines configuration, component scanning, and auto-configuration,
// allowing Spring Boot to automatically configure the application and discover components such as
// controllers, services, and repositories.