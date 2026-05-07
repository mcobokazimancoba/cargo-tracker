-- =============================================================================
-- Cargo Tracker — PostgreSQL schema
-- =============================================================================
-- Hand-derived from the @Entity classes under src/main/java/com/cargotracker/entity/.
-- Run this once against an empty database after creating the role and DB:
--
--   CREATE DATABASE cargo_tracker;
--   CREATE USER cargo_app WITH ENCRYPTED PASSWORD '<choose>';
--   GRANT ALL PRIVILEGES ON DATABASE cargo_tracker TO cargo_app;
--
--   psql -U cargo_app -d cargo_tracker -f db/schema.sql
--
-- persistence.xml is set to schema-generation.database.action=none, so the
-- app never alters DDL on startup. That is intentional — schema changes
-- belong in version-controlled migrations, not in JPA's "auto-create" mode
-- which is only safe on greenfield dev databases.
-- =============================================================================


-- ── Sequences ──────────────────────────────────────────────────────────────
-- Names match @SequenceGenerator(sequenceName = ...) on the @Entity classes.

CREATE SEQUENCE IF NOT EXISTS app_user_id_seq        START 1 INCREMENT 10;
CREATE SEQUENCE IF NOT EXISTS location_id_seq        START 1 INCREMENT 50;
CREATE SEQUENCE IF NOT EXISTS tracking_event_id_seq  START 1 INCREMENT 100;


-- ── Users ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS app_users (
    id              BIGINT       PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(128) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL,
    last_login_at   TIMESTAMP,

    CONSTRAINT uq_user_email    UNIQUE (email),
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT ck_user_role     CHECK  (role IN ('CUSTOMER', 'OPERATOR', 'ADMIN'))
);

CREATE INDEX IF NOT EXISTS idx_user_role   ON app_users (role);
CREATE INDEX IF NOT EXISTS idx_user_active ON app_users (active);


-- ── Locations ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS locations (
    id        BIGINT       PRIMARY KEY,
    unlocode  VARCHAR(5)   NOT NULL,
    city      VARCHAR(100) NOT NULL,
    country   VARCHAR(100) NOT NULL,

    CONSTRAINT uq_location_code UNIQUE (unlocode)
);


-- ── Cargo ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS cargos (
    id                  BIGSERIAL      PRIMARY KEY,
    tracking_number     VARCHAR(20)    NOT NULL,
    description         VARCHAR(500),
    weight_kg           NUMERIC(10,3),
    status              VARCHAR(20)    NOT NULL DEFAULT 'BOOKED',
    origin_id           BIGINT,
    destination_id      BIGINT,
    customer_username   VARCHAR(50),
    expected_arrival    DATE,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,

    CONSTRAINT uq_cargo_tracking UNIQUE (tracking_number),
    CONSTRAINT ck_cargo_status   CHECK  (status IN
        ('BOOKED', 'IN_TRANSIT', 'AT_DESTINATION', 'DELIVERED', 'CANCELLED')),

    CONSTRAINT fk_cargo_origin
        FOREIGN KEY (origin_id)      REFERENCES locations (id),
    CONSTRAINT fk_cargo_destination
        FOREIGN KEY (destination_id) REFERENCES locations (id)
);

CREATE INDEX IF NOT EXISTS idx_cargo_customer ON cargos (customer_username);
CREATE INDEX IF NOT EXISTS idx_cargo_status   ON cargos (status);


-- ── Tracking events ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS tracking_events (
    id            BIGINT       PRIMARY KEY,
    cargo_id      BIGINT       NOT NULL,
    event_type    VARCHAR(30)  NOT NULL,
    location_id   BIGINT       NOT NULL,
    occurred_at   TIMESTAMP    NOT NULL,
    recorded_at   TIMESTAMP    NOT NULL,
    notes         VARCHAR(1000),

    CONSTRAINT ck_event_type CHECK (event_type IN
        ('PICKED_UP', 'DEPARTED', 'ARRIVED_AT_PORT', 'DELIVERED', 'EXCEPTION')),

    CONSTRAINT fk_event_cargo
        FOREIGN KEY (cargo_id)    REFERENCES cargos    (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_location
        FOREIGN KEY (location_id) REFERENCES locations (id)
);

CREATE INDEX IF NOT EXISTS idx_event_cargo       ON tracking_events (cargo_id);
CREATE INDEX IF NOT EXISTS idx_event_occurred_at ON tracking_events (occurred_at);
CREATE INDEX IF NOT EXISTS idx_event_type        ON tracking_events (event_type);


-- ── Password reset tokens ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token       VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_prt_user
        FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);

-- Unique index on the bearer token — every lookup hits this index.
CREATE UNIQUE INDEX IF NOT EXISTS idx_prt_token ON password_reset_tokens (token);


-- =============================================================================
-- Seed data — minimum needed for the app to start usefully.
-- The locations and one ADMIN user are the only system data; everything
-- else is created by registered users.
-- =============================================================================

-- Three locations covering the three sample routes used in the booking UI.
INSERT INTO locations (id, unlocode, city, country) VALUES
    (nextval('location_id_seq'), 'ZAJNB', 'Johannesburg',  'South Africa'),
    (nextval('location_id_seq'), 'NLRTM', 'Rotterdam',     'Netherlands'),
    (nextval('location_id_seq'), 'USNYC', 'New York',      'United States')
ON CONFLICT (unlocode) DO NOTHING;

-- Note on initial admin: not seeded here because the password hash format
-- depends on AuthService's PBKDF2 parameters. Create the first admin via
-- POST /api/auth/register (creates a CUSTOMER) and then promote them with
-- a one-off SQL update:
--
--   UPDATE app_users SET role = 'ADMIN' WHERE username = 'your-admin';
