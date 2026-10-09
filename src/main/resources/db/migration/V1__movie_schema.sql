-- Identity cache only. user_id columns elsewhere are deliberately NOT foreign keys:
-- identities come from an external IdP and must not block the hot booking path.
CREATE TABLE users (
    id           VARCHAR(64)  PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    role         VARCHAR(16)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE movies (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    duration_minutes INT          NOT NULL CHECK (duration_minutes > 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE theatres (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL
);

CREATE TABLE screens (
    id         BIGSERIAL PRIMARY KEY,
    theatre_id BIGINT       NOT NULL REFERENCES theatres (id),
    code       VARCHAR(40)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    CONSTRAINT uq_screens_theatre_code UNIQUE (theatre_id, code)
);

-- Physical seats: static, never locked for booking purposes.
CREATE TABLE seats (
    id        BIGSERIAL PRIMARY KEY,
    screen_id BIGINT      NOT NULL REFERENCES screens (id),
    label     VARCHAR(8)  NOT NULL,
    CONSTRAINT uq_seats_screen_label UNIQUE (screen_id, label)
);

CREATE TABLE shows (
    id          BIGSERIAL PRIMARY KEY,
    public_id   VARCHAR(40)  NOT NULL UNIQUE,
    movie_id    BIGINT       NOT NULL REFERENCES movies (id),
    screen_id   BIGINT       NOT NULL REFERENCES screens (id),
    starts_at   TIMESTAMPTZ  NOT NULL,
    price_minor BIGINT       NOT NULL CHECK (price_minor >= 0),
    currency    CHAR(3)      NOT NULL DEFAULT 'INR',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_shows_movie_starts ON shows (movie_id, starts_at);

CREATE TABLE holds (
    id         VARCHAR(40)  PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    show_id    BIGINT       NOT NULL REFERENCES shows (id),
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'CONFIRMED', 'EXPIRED', 'RELEASED')),
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);
-- Partial index: the sweeper only ever looks at ACTIVE holds ordered by expiry.
CREATE INDEX idx_holds_active_expires ON holds (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_holds_user ON holds (user_id);

-- Per-show inventory. A1 can be BOOKED for show Y while AVAILABLE for show X.
CREATE TABLE show_seats (
    id              BIGSERIAL   PRIMARY KEY,
    show_id         BIGINT      NOT NULL REFERENCES shows (id),
    seat_id         BIGINT      NOT NULL REFERENCES seats (id),
    status          VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    hold_id         VARCHAR(40) REFERENCES holds (id),
    hold_expires_at TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_show_seats_show_seat UNIQUE (show_id, seat_id),
    -- Last line of defence: the row can never describe an impossible state.
    CONSTRAINT ck_show_seats_state CHECK (
        (status = 'AVAILABLE' AND hold_id IS NULL AND hold_expires_at IS NULL)
     OR (status = 'HELD'      AND hold_id IS NOT NULL AND hold_expires_at IS NOT NULL)
     OR (status = 'BOOKED'    AND hold_id IS NOT NULL)
    )
);
CREATE INDEX idx_show_seats_show_status ON show_seats (show_id, status);
CREATE INDEX idx_show_seats_hold ON show_seats (hold_id) WHERE hold_id IS NOT NULL;
CREATE INDEX idx_show_seats_held_expiry ON show_seats (hold_expires_at) WHERE status = 'HELD';

CREATE TABLE hold_items (
    id           BIGSERIAL   PRIMARY KEY,
    hold_id      VARCHAR(40) NOT NULL REFERENCES holds (id),
    show_seat_id BIGINT      NOT NULL REFERENCES show_seats (id),
    CONSTRAINT uq_hold_items UNIQUE (hold_id, show_seat_id)
);

CREATE TABLE bookings (
    id                 BIGSERIAL    PRIMARY KEY,
    public_id          VARCHAR(40)  NOT NULL UNIQUE,
    user_id            VARCHAR(64)  NOT NULL,
    show_id            BIGINT       NOT NULL REFERENCES shows (id),
    hold_id            VARCHAR(40)  NOT NULL UNIQUE REFERENCES holds (id),  -- one booking per hold
    status             VARCHAR(20)  NOT NULL CHECK (status IN
                       ('INITIATED', 'PAYMENT_PENDING', 'CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED', 'EXPIRED')),
    total_amount_minor BIGINT       NOT NULL CHECK (total_amount_minor >= 0),
    currency           CHAR(3)      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_bookings_user ON bookings (user_id, created_at DESC);

CREATE TABLE booking_items (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT    NOT NULL REFERENCES bookings (id),
    show_seat_id BIGINT    NOT NULL REFERENCES show_seats (id),
    price_minor  BIGINT    NOT NULL CHECK (price_minor >= 0)
);
CREATE INDEX idx_booking_items_booking ON booking_items (booking_id);

CREATE TABLE payments (
    id           BIGSERIAL    PRIMARY KEY,
    provider_ref VARCHAR(40)  NOT NULL UNIQUE,
    booking_id   BIGINT       NOT NULL UNIQUE REFERENCES bookings (id),
    amount_minor BIGINT       NOT NULL CHECK (amount_minor >= 0),
    currency     CHAR(3)      NOT NULL,
    status       VARCHAR(20)  NOT NULL CHECK (status IN
                 ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUND_REQUIRED', 'REFUNDED')),
    reason       VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_payments_status ON payments (status) WHERE status = 'REFUND_REQUIRED';

CREATE TABLE idempotency_keys (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);

-- Consumer-side deduplication for at-least-once Kafka delivery.
CREATE TABLE processed_events (
    event_id     VARCHAR(64) NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);
