# Cargo Tracker

A Jakarta EE 10 REST API for managing cargo shipments — booking, tracking,
status events, and operational analytics. Built as a final-year ICT
portfolio project; the focus is on the security work in
`feature/auth-and-security` (see [Security](#security)).

---

## Stack

| Layer        | Choice                                       |
| ------------ | -------------------------------------------- |
| Language     | Java 17 (LTS)                                |
| Platform     | Jakarta EE 10 (full profile)                 |
| App server   | GlassFish 7                                  |
| Persistence  | EclipseLink JPA over PostgreSQL              |
| Build        | Maven 3.9+                                   |
| Auth         | JWT (HS256, jjwt 0.12.6) over Bearer tokens  |
| Passwords    | PBKDF2-HMAC-SHA256, 310,000 iterations       |
| Validation   | Jakarta Bean Validation 3.0 (Hibernate impl) |
| Mail         | Jakarta Mail (built into GlassFish 7)        |

---

## Quick start

### Prerequisites

- JDK 17
- Maven 3.9+
- PostgreSQL 14+
- GlassFish 7

### 1. Database

Create the database and a role for the app:

```sql
CREATE DATABASE cargo_tracker;
CREATE USER cargo_app WITH ENCRYPTED PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE cargo_tracker TO cargo_app;
```

The schema is **not** auto-generated. `persistence.xml` is set to
`schema-generation.database.action=none` so the app never touches DDL on
startup. Run the committed DDL once after creating the database:

```bash
psql -U cargo_app -d cargo_tracker -f db/schema.sql
```

That file lives at [`db/schema.sql`](db/schema.sql). It seeds three
sample locations (Johannesburg, Rotterdam, New York) and explains how to
promote your first registered user to `ADMIN` in a comment at the bottom.

### 2. JDBC connection pool in GlassFish

The app looks up its DataSource via JNDI as `jdbc/cargoTrackerDS`. DB
credentials live in the GlassFish admin console, **not** in `.env` (see
[Configuration → why DB creds aren't in .env](#why-database-credentials-arent-in-env)).

Admin console → Resources → JDBC → JDBC Connection Pools → New:

| Field             | Value                                |
| ----------------- | ------------------------------------ |
| Pool name         | `cargoTrackerPool`                   |
| Resource type     | `javax.sql.DataSource`               |
| Database driver   | `org.postgresql.ds.PGSimpleDataSource` |
| ServerName        | `localhost`                          |
| PortNumber        | `5432`                               |
| DatabaseName      | `cargo_tracker`                      |
| User              | `cargo_app`                          |
| Password          | (your password)                      |

Then JDBC Resources → New: JNDI name `jdbc/cargoTrackerDS`, pool
`cargoTrackerPool`. Ping the pool to verify.

### 3. App secrets

```bash
cp .env.example .env
# Open .env and fill in:
#   JWT_SECRET   — generate with: openssl rand -base64 48
#   MAIL_SMTP_*  — Mailtrap (https://mailtrap.io) is recommended for dev
```

Apply the values to GlassFish in one of three ways
(see [Configuration](#configuration) for details):

```bash
# Option A — source the file before starting the domain
set -a && source .env && set +a
asadmin start-domain
```

### 4. Build and deploy

```bash
mvn clean package
asadmin deploy --force=true target/cargo-tracker.war
```

The API will be available at:

```
http://localhost:8080/Cargo_Tracker_System/api/health
```

(The context root `/Cargo_Tracker_System` is fixed in
`src/main/webapp/WEB-INF/glassfish-web.xml`.)

---

## Configuration

All runtime configuration is read from system properties or environment
variables. Resolution order at every read site is:

```
-Dproperty.name=value   (JVM option)
        ↓ if absent
PROPERTY_NAME            (process env var)
        ↓ if absent
built-in default
```

The complete list lives in [`.env.example`](.env.example). Highlights:

| Variable                        | Default      | Purpose                              |
| ------------------------------- | ------------ | ------------------------------------ |
| `JWT_SECRET`                    | random / JVM | HS256 signing key (32+ bytes)        |
| `MAIL_SMTP_HOST`                | mailtrap.io  | Outbound SMTP                        |
| `APP_BASE_URL`                  | localhost    | Used to build links in emails        |
| `RATELIMIT_REQUESTS`            | 100          | Per-IP request cap                   |
| `RATELIMIT_WINDOW_SECONDS`      | 60           | Per-IP window                        |
| `LOGIN_THROTTLE_MAX_FAILURES`   | 5            | Login brute-force cap per (IP, user) |
| `LOGIN_THROTTLE_WINDOW_SECONDS` | 900          | Login throttle window                |

### Why database credentials aren't in `.env`

Per Jakarta EE convention, the JDBC connection pool is configured inside
the container (GlassFish admin console). The app references it via JNDI
(`jdbc/cargoTrackerDS`) and never sees the password. Putting DB creds
into env vars on a Java EE app would bypass the container's pooling,
TLS handling, and connection-leak detection — counter to the platform's
design.

---

## API reference

Base path: `/api`

`*` in the **Auth** column means a valid Bearer JWT must be sent in the
`Authorization` header.

### Authentication

| Method | Path                                | Auth | Notes                                                            |
| ------ | ----------------------------------- | ---- | ---------------------------------------------------------------- |
| POST   | `/api/auth/register`                | —    | Self-service register as `CUSTOMER`; sends a verification email  |
| GET    | `/api/auth/verify?token=…`          | —    | Confirms ownership of the email; returns a small HTML page       |
| POST   | `/api/auth/resend-verification`     | —    | Always 200 (no enumeration); re-issues if account is unverified  |
| POST   | `/api/auth/login`                   | —    | Returns a signed JWT (HS256); 403 if email not yet verified      |
| POST   | `/api/auth/forgot-password`         | —    | Always 200 (no account enumeration)                              |
| POST   | `/api/auth/reset-password`          | —    | Single-use token, 15-minute TTL                                  |

### Cargo

| Method | Path                                         | Auth | Allowed roles                |
| ------ | -------------------------------------------- | ---- | ---------------------------- |
| POST   | `/api/cargos`                                | *    | CUSTOMER, OPERATOR, ADMIN    |
| GET    | `/api/cargos`                                | *    | OPERATOR, ADMIN              |
| GET    | `/api/cargos/customer/mine`                  | *    | Any (filtered to caller)     |
| GET    | `/api/cargos/{trackingNumber}`               | opt  | Public + ownership check     |
| PUT    | `/api/cargos/{trackingNumber}`               | *    | OPERATOR, ADMIN              |
| POST   | `/api/cargos/{trackingNumber}/events`        | *    | OPERATOR, ADMIN              |
| DELETE | `/api/cargos/{trackingNumber}`               | *    | ADMIN                        |
| DELETE | `/api/cargos/{trackingNumber}/permanent`     | *    | ADMIN                        |

`{trackingNumber}` must match `CGO-[A-Z0-9]{10}` (validated at the
framework boundary). The track lookup is **public for guests** but
enforces ownership when an authenticated `CUSTOMER` calls it — see
[Security → Authorization model](#authorization-model).

### Locations

| Method | Path                       | Auth | Allowed roles |
| ------ | -------------------------- | ---- | ------------- |
| GET    | `/api/locations`           | —    | Public        |
| GET    | `/api/locations?search=…`  | —    | Public        |
| POST   | `/api/locations`           | *    | ADMIN         |

### Analytics

| Method | Path                                  | Auth | Allowed roles    |
| ------ | ------------------------------------- | ---- | ---------------- |
| GET    | `/api/analytics/summary`              | *    | OPERATOR, ADMIN  |
| GET    | `/api/analytics/status-breakdown`     | *    | OPERATOR, ADMIN  |
| GET    | `/api/analytics/top-routes`           | *    | OPERATOR, ADMIN  |
| GET    | `/api/analytics/volume?days=30`       | *    | OPERATOR, ADMIN  |
| GET    | `/api/analytics/top-locations`        | *    | OPERATOR, ADMIN  |

### Health

| Method | Path           | Auth | Notes |
| ------ | -------------- | ---- | ----- |
| GET    | `/api/health`  | —    | LB / monitoring probe |

### Standard error shape

All non-2xx responses use this body:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid token",
  "timestamp": "2026-05-07T22:14:09.123"
}
```

`429 Too Many Requests` responses include a `Retry-After` header
(seconds).

---

## Security

This section is the point of the project. Each subsection below
corresponds to one commit on `feature/auth-and-security` so the git
history reads as the security narrative.

### Filter pipeline

Every request flows through a deliberately ordered chain of JAX-RS
filters. Priority is set by `@Priority` on each filter; lower runs
first.

| Order | Filter             | Role                                     |
| ----- | ------------------ | ---------------------------------------- |
| 100   | `RateLimitFilter`  | Per-IP cap (100/60s default)             |
| 500   | `CorsFilter`       | OPTIONS preflight + CORS headers         |
| 1000  | `AuthFilter`       | JWT verification, attaches `AppUser`     |
| 2000  | `RoleFilter`       | RBAC by HTTP method + path pattern       |

Rate limit runs first so an attacker's flood doesn't cost CPU on
signature verification. CORS sits second so the headers it adds reach
even aborted (4xx) responses — without that, browsers would surface
"CORS error" instead of the real status code.

### Password storage — PBKDF2-HMAC-SHA256

`AuthService` hashes passwords with **PBKDF2-HMAC-SHA256, 310,000
iterations, 16-byte salt, 256-bit key**. Why PBKDF2 over BCrypt:

- It ships with the JDK (`javax.crypto.SecretKeyFactory`) — no extra
  dependency to vet, version, or update.
- 310,000 iterations meets the current OWASP Password Storage cheat
  sheet recommendation for PBKDF2-HMAC-SHA256.
- Verification uses `MessageDigest.isEqual` (constant-time compare) to
  prevent timing oracles.

The hash is stored as `base64(salt) + ":" + base64(key)`.

### Tokens — signed JWT (HS256)

Login returns an **HS256-signed JWT** built and verified with jjwt
0.12.6. Claims:

```
iss: cargo-tracker
sub: <username>
iat: <issued-at, unix seconds>
exp: <expires-at, 8h after iat>
```

The signing key comes from `JWT_SECRET` (env var) or `-Djwt.secret`
(JVM option). If neither is set the service generates a random per-JVM
key at startup and logs a stern WARNING — tokens then die on every
restart, a noisy and self-correcting failure mode (preferable to a
hard-coded default that would ship in the repo).

Roles are deliberately **not** in the token. `AuthFilter` loads the
live `AppUser` from the database on every request, so a role change
takes effect on the next request — no need to wait 8 hours for a token
to expire after a privilege revocation.

### Authorization model

Two layers of authz:

1. **`RoleFilter`** — coarse RBAC by HTTP method + path pattern. e.g.
   `DELETE cargos/.+ → ADMIN only`. Static, request-scoped, fast.
2. **Domain checks in services** — `CargoService.findByTrackingNumberAuthorized`
   enforces "a CUSTOMER can only see their own cargo". Lives in the
   service so any future endpoint that fetches cargo on a caller's
   behalf gets the same rule for free.

Public guest tracking (`GET /api/cargos/{tn}` with no token) still
works — the booking-confirmation email link uses it.

### Input validation

Standard Jakarta Bean Validation across:

- Every request DTO (`@NotBlank`, `@Email`, `@Size`, `@Pattern`,
  `@DecimalMax`, `@PastOrPresent`).
- Every JAX-RS path and query parameter (`@Pattern` on tracking
  numbers, `@Min` / `@Max` on page and size).

Format constants are centralised on the entities so DTOs and resources
reference one source of truth:

```java
Cargo.TRACKING_NUMBER_PATTERN     = "CGO-[A-Z0-9]{10}"
Location.UNLOCODE_PATTERN         = "[A-Z]{2}[A-Z0-9]{3}"
RegisterRequest.username @Pattern = "^[A-Za-z0-9._-]+$"   // whitelist
```

The username pattern is a **whitelist**, not a blacklist: blacklists
inevitably miss something (zero-width unicode, control chars, RTL
overrides); a whitelist of safe characters is unambiguous in code
review.

### Rate limiting (`RateLimitFilter`)

- Sliding window over a per-IP `Deque<Long>` of timestamps. More
  accurate than fixed windows (no 2× burst at boundaries), simpler
  than token bucket.
- 100 requests / 60 seconds default; tunable via env vars.
- Per-deque synchronisation, not a global lock — contention is per-IP.
- OPTIONS preflights exempt; counting them would unfairly drain a
  SPA user's budget on browser overhead.
- Uses `getRemoteAddr()`. **`X-Forwarded-For` is deliberately not
  honoured** — without a known reverse proxy in front, any client could
  spoof the header and bypass the limit. If/when this is deployed
  behind nginx/cloudflare/etc., switch to reading the first XFF entry.

### Brute-force protection on login (`LoginThrottleService`)

Stricter, scoped only to `/api/auth/login`:

- 5 failed attempts / 15 minutes per **(IP, username)**.
- The throttle check runs **before** the password verify, so an
  attacker can't time the response (PBKDF2 takes ~100ms; locked
  replies are instant) to learn which usernames are valid.
- A successful login clears the bucket — a user who fat-fingers their
  password three times then gets it right isn't one typo away from a
  lockout.
- Only 401 (invalid credentials) increments the counter. 400
  (validation) and 500 don't — those aren't brute-force signals, and
  counting them would let an attacker DoS legitimate users by sending
  malformed JSON.

### Account-enumeration defences

- `forgot-password` always returns 200 whether or not the email is
  registered.
- `resend-verification` always returns 200 whether the email is
  registered, already verified, or unknown.
- `reset-password` and `verify` both return the same generic message
  for every failure cause (token unknown / used / expired) — no signal
  of which.
- The login throttle replies in constant time when locked, so an
  attacker can't probe for valid usernames by timing.
- **Login order of operations** — see [Email verification](#email-verification)
  below; password is verified *before* the email-verified flag, so a
  wrong password gets the same generic 401 whether the account is
  verified or not.
- Reset and verification tokens are **never logged** in plaintext
  (they're bearer credentials — anyone holding the token can change
  the password / confirm the address).

### Email verification

New accounts are blocked from logging in until they click the link
mailed at registration. Three endpoints make up the flow:

```
POST /api/auth/register                ← creates AppUser(email_verified=false)
                                         + issues + mails a single-use token (24 h TTL)
GET  /api/auth/verify?token=…          ← flips email_verified=true; renders an HTML page
POST /api/auth/resend-verification     ← {email}; always 200, re-issues if applicable
```

**Why the password is verified before the verification flag is checked
in `login`.** The interview-defensible sequence is:

1. Lookup user by username — generic 401 if not found.
2. Verify password — generic 401 on mismatch.
3. Check `email_verified` — 403 with the verify-email message if false.
4. Issue JWT.

If we checked the flag before the password, an attacker could submit
*any* password and learn that the username exists in an unverified
state. That turns login into an account-enumeration oracle. By
verifying the password first, only a caller who already knows the
correct password ever sees the verification-needed message — at which
point the existence of the account isn't a secret to them.

**The verify-email page is plain HTML.** Users land there by clicking
a link in their inbox, so JSON would be hostile UX. The page is
self-contained (no SPA dependency) so the link still works if the
frontend is unreachable, and the username — the only piece of
user-supplied data rendered — is HTML-escaped.

### Secrets handling

Every secret the app reads is documented in
[`.env.example`](.env.example). `.env` itself is gitignored.

DB credentials are kept out of `.env` on purpose — see
[Why database credentials aren't in `.env`](#why-database-credentials-arent-in-env)
above.

---

## Project structure

```
src/main/java/com/cargotracker/
├── api/                # JAX-RS resources (HTTP layer, kept thin)
│   ├── AuthResource.java
│   ├── CargoResource.java
│   ├── HealthResource.java
│   └── LocationAnalyticsResource.java
├── config/             # JAX-RS filters and JAX-RS Application
│   ├── AuthFilter.java
│   ├── CorsFilter.java
│   ├── JakartaRestConfig.java
│   ├── RateLimitFilter.java
│   └── RoleFilter.java
├── dto/
│   ├── request/        # Inbound DTOs with @Valid annotations
│   └── response/       # Outbound DTOs
├── entity/             # JPA entities (AppUser, Cargo, Location, …)
├── exception/          # Domain exceptions + JAX-RS ExceptionMappers
├── repository/         # Thin data-access wrappers around EntityManager
└── service/            # Business logic (where authz domain rules live)
    ├── AuthService.java
    ├── CargoService.java
    ├── LoginThrottleService.java
    └── MailService.java
```

---

## Tests

```bash
mvn test
```

A small, focused JUnit 5 + Mockito suite under `src/test/java/`. Three
test classes, 17 methods total — deliberately not exhaustive (full
coverage was out of scope for this branch). They exercise the security-
critical paths added in this branch:

| Test class                         | What it proves                                             |
| ---------------------------------- | ---------------------------------------------------------- |
| `AuthServiceTest`                  | Registration normalises, hashes, and triggers verification mail; login round-trips a JWT; wrong password / forged signature → 401; unverified email → 403 only after correct password (no enumeration) |
| `LoginThrottleServiceTest`         | Brute-force lockout fires at exactly N failures, isolates per (IP, user), survives case-rotation, clears on success, ages out |
| `CargoServiceAuthorizationTest`    | Per-customer ownership rule on the protected tracking lookup (guest / OPERATOR / ADMIN / owner-CUSTOMER all allowed; non-owner CUSTOMER → 403) |

Why unit tests not Arquillian: every dependency on these services is
injectable, the behaviours under test are pure business logic, and the
PBKDF2 hash + JWT round-trip work without a JTA transaction. Integration
tests would be 10× slower for no extra coverage.

---

## Branch / commit narrative

`feature/auth-and-security` was built in seven small commits so the
diff for each piece of the security story can be reviewed in isolation.
View with:

```bash
git log --oneline main..feature/auth-and-security
```
