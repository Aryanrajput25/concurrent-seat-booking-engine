# Learning this project from scratch

This doc walks through the codebase in the order I'd read it if I were
picking it up for the first time — no prior context needed beyond basic
Spring Boot and SQL. All code lives under the base package
`com.aryanrajput.seatvault`; paths below are relative to it.

## The problem, in one sentence

If two users try to book the same movie seat at the same moment, exactly one
of them should get it — never both, never neither.

## The three moving pieces

1. **Spring Boot** runs the HTTP server and routes requests to
   `BookingController`, which delegates everything to the service layer.
2. **MySQL** is durable storage — theatres, screens, seats, shows, bookings,
   and (critically) which seat belongs to which booking right now. Data
   survives an app restart.
3. **Redis** is temporary, fast storage. When someone selects a seat, a key
   like `seat-lock:<showId>:<seatId>` is written with a short TTL. This is
   the *first* line of defense against two people grabbing the same seat —
   but it's not the final word. MySQL's `show_seat_reservations` table is.

## Why two locking layers instead of one

A lock that lives only in Redis has a well-known failure mode: if the TTL
expires while a payment is still being processed (slow network, retried
request, whatever), a second user's Redis lock can succeed and "steal" the
seat out from under the first booking, which might still go on to confirm
successfully — now two people have paid for the same seat.

This project avoids that by treating MySQL as the real authority. Every
state change — claiming a hold, confirming after payment, releasing on
cancellation or timeout — re-locks the seat's row with
`SELECT ... FOR UPDATE` and re-checks who currently owns it, before doing
anything. So even if a Redis lock has expired early, the booking that relied
on it will discover, under a database lock, that it no longer holds the
seat — and will be rejected instead of confirmed.

## Booking lifecycle

```
PENDING --(payment succeeds)--------------------> CONFIRMED
PENDING --(too many payment failures, or timeout)-> EXPIRED
PENDING --(user cancels)--------------------------> CANCELLED
```

`PENDING` is the only state that can still change. The other three are
final.

## Suggested reading order

1. **`booking/api/BookingController.java`** — the full list of endpoints and
   what they accept/return. Start here to get the shape of the API.
2. **`booking/domain/`** — the entities, especially `ShowSeatReservation`
   (the authoritative ownership row) and `Booking`. Read alongside
   **`src/main/resources/db/migration/`** to see the actual tables and
   constraints these map to.
3. **`booking/service/BookingService.java`** — the whole booking lifecycle:
   create, pay, fail, cancel, and the background expiry sweep. This is where
   the row-locking pattern described above actually happens.
4. **`booking/service/SeatLockService.java`** — the Redis fast-path lock,
   read last since it only makes sense once you've seen what it's a
   fast-path *in front of*.

## Running it locally

You'll need Docker, Java 17, and Maven.

**Simplest — everything in Docker:**

```bash
docker compose up --build
```

**Faster loop while actively editing code — infra in Docker, app on the host:**

```bash
docker compose up -d mysql redis
mvn spring-boot:run
```

Either way, the API comes up at `http://localhost:8080`.

## A first walkthrough in Postman (or curl)

Create things in this order, using the `id` returned by each response in the
next request.

```http
POST /api/theatres
Content-Type: application/json

{"name": "PVR Forum"}
```

```http
POST /api/theatres/1/screens
Content-Type: application/json

{"name": "Screen 1"}
```

```http
POST /api/screens/1/seats
Content-Type: application/json

{"rowNumber": 1, "seatNumber": 1}
```

```http
POST /api/movies
Content-Type: application/json

{"name": "Interstellar"}
```

```http
POST /api/shows
Content-Type: application/json

{"movieId": 1, "screenId": 1, "startTime": "2026-12-01T18:00:00Z", "durationMinutes": 169}
```

Now check the seat map:

```http
GET /api/shows/1/seats
```

Hold seat 1:

```http
POST /api/bookings
Content-Type: application/json

{"userId": "u1", "showId": 1, "seatIds": [1]}
```

And confirm it:

```http
POST /api/payments/1/success
Content-Type: application/json

{"userId": "u1"}
```

Check the seat map again — seat 1 now shows `"available": false` permanently.

## Where to look if you want to prove the concurrency claims yourself

`src/test/java/com/aryanrajput/seatvault/booking/ConcurrentBookingIntegrationTest.java`
fires real concurrent HTTP requests (via a thread pool) at a real running
instance of the app, backed by real MySQL and Redis containers started with
Testcontainers. Run it with `mvn test` (Docker must be running). It's the
most convincing way to see the locking actually work, rather than take it on
faith from reading the code.
