# SeatVault

A Spring Boot REST API for booking movie-theatre seats that stays correct
under concurrent load — two people racing for the same seat can never both
win it.

```text
Client / Postman -> Spring Boot REST API -> Booking Service
                                             |-> Redis (short-lived seat locks)
                                             |-> MySQL (persistent, authoritative data)
                                             -> Payment workflow
```

## Why this is harder than it looks

The interesting problem in a seat-booking system isn't the CRUD — it's making
sure that when two users try to book the same seat at the same instant,
exactly one of them succeeds, with no gap where both think they've won. This
project solves that with two layers working together:

1. **Redis** (`SeatLockService`) is a fast, short-lived lock. It rejects
   almost all contention for the same seat in microseconds, using a single
   Lua script so that a multi-seat booking is locked all-or-nothing, and
   released only if the caller still owns it.
2. **MySQL** (`show_seat_reservations`) is the *authoritative* source of
   truth. Every real state change — claiming a hold, confirming on payment,
   releasing on cancellation or timeout — locks that seat's row with
   `SELECT ... FOR UPDATE` before touching it. This is what actually
   guarantees correctness: a Redis lock that expires early while a payment
   is still processing can never let a stale booking win, because the
   confirm step re-checks ownership under a database lock, not a cache.
3. As a final defense in depth, `confirmed_show_seats` has a database-level
   unique constraint on `(show_id, seat_id)` — even if application logic
   somewhere had a bug, MySQL itself would refuse a duplicate confirmed
   booking.

Concurrent show scheduling is handled the same way: creating a show locks
the target screen's row first, so two requests can't both schedule
overlapping shows on it.

## Running it

**Everything in Docker (simplest):**

```bash
docker compose up --build
```

This starts the app, MySQL, and Redis together. The API is available at
`http://localhost:8080` once MySQL's healthcheck passes.

**App on the host, infrastructure in Docker (faster edit-compile loop):**

```bash
docker compose up -d mysql redis
mvn spring-boot:run
```

Stop everything with `docker compose down`.

See [LEARN_FROM_SCRATCH.md](LEARN_FROM_SCRATCH.md) for a guided walkthrough
of the code and ready-to-copy Postman requests.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/theatres` | Create a theatre |
| `POST` | `/api/theatres/{id}/screens` | Add a screen to a theatre |
| `POST` | `/api/screens/{id}/seats` | Add a seat to a screen |
| `POST` | `/api/movies` | Create a movie |
| `POST` | `/api/shows` | Schedule a show (rejects overlapping shows on the same screen) |
| `GET` | `/api/shows/{id}` | Show details |
| `GET` | `/api/shows/{id}/seats` | Seat map with live availability |
| `POST` | `/api/bookings` | Hold one or more seats for a show |
| `POST` | `/api/bookings/{id}/cancel` | Cancel a pending booking |
| `POST` | `/api/payments/{id}/success` | Confirm a booking |
| `POST` | `/api/payments/{id}/failure` | Record a failed payment attempt |

Errors are returned as `{"error": "..."}` with a matching HTTP status:
`404` for missing resources, `400` for invalid input or the wrong user
acting on a booking, `409` for anything that's a legitimate conflict
(seat unavailable, hold expired, overlapping show).

## Verifying the concurrency guarantees

```bash
mvn test
```

`ConcurrentBookingIntegrationTest` starts real MySQL and Redis containers via
Testcontainers and drives the app over real HTTP, not by calling service
methods directly. It covers:

- 50 concurrent requests for **one** seat → exactly 1 succeeds, 49 get `409`.
- 50 concurrent requests across **10** seats → exactly 10 succeed, with no
  seat double-booked.
- A booking whose hold has expired cannot confirm once another booking has
  claimed the same seat in MySQL.
- Cancelling and paying for the same booking at the same time → exactly one
  of the two wins.
- Two requests to schedule overlapping shows on the same screen → exactly
  one succeeds.

Docker must be running locally for this test, since it needs to start
containers.

## Configuration

All of these have sensible defaults (see `application.yml`) and can be
overridden with environment variables:

| Variable | Default | Meaning |
|---|---|---|
| `LOCK_TIMEOUT_SECONDS` | `300` | How long a seat hold is valid before it's eligible for expiry |
| `ALLOWED_PAYMENT_FAILURES` | `2` | Failed payment attempts allowed before a booking expires |
| `EXPIRATION_SWEEP_SECONDS` | `15` | How often the background job checks for stale holds |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local MySQL | Database connection |
| `REDIS_HOST`, `REDIS_PORT` | `localhost`, `6379` | Redis connection |

## Known limitations / next steps

- No `GET /api/bookings/{id}` yet — a booking's state is only returned by the
  action that created or changed it.
- No idempotency key on `POST /api/bookings`, so a client retry after a
  network timeout could create an extra `PENDING` row (it will still only
  ever win a seat once, and expires on its own via the background sweep).
- No path to cancel or refund a booking that has already been confirmed.
- If Redis is unreachable, booking requests fail outright rather than
  degrading gracefully to a MySQL-only check.

## Project layout

```
src/main/java/com/aryanrajput/seatvault/
  SeatVaultApplication.java      Spring Boot entry point
  booking/domain/                JPA entities
  booking/repo/                  Spring Data repositories (locking queries live here)
  booking/service/                BookingService, ShowService, SeatLockService
  booking/api/                   REST controller, DTOs, exception handling
src/main/resources/
  application.yml
  db/migration/                  Flyway-managed MySQL schema
src/test/java/com/aryanrajput/seatvault/booking/
  ConcurrentBookingIntegrationTest.java
```
