CREATE TABLE trains (
    id                  BIGSERIAL    PRIMARY KEY,
    train_no            VARCHAR(10)  NOT NULL UNIQUE,
    name                VARCHAR(120) NOT NULL,
    source_station      VARCHAR(80)  NOT NULL,
    destination_station VARCHAR(80)  NOT NULL
);

CREATE TABLE train_schedules (
    id           BIGSERIAL   PRIMARY KEY,
    train_id     BIGINT      NOT NULL REFERENCES trains (id),
    journey_date DATE        NOT NULL,
    departure_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_train_schedule UNIQUE (train_id, journey_date)
);

CREATE TABLE coaches (
    id       BIGSERIAL  PRIMARY KEY,
    train_id BIGINT     NOT NULL REFERENCES trains (id),
    code     VARCHAR(8) NOT NULL,
    CONSTRAINT uq_coaches UNIQUE (train_id, code)
);

CREATE TABLE berths (
    id           BIGSERIAL   PRIMARY KEY,
    coach_id     BIGINT      NOT NULL REFERENCES coaches (id),
    berth_number INT         NOT NULL CHECK (berth_number > 0),
    ordinal      INT         NOT NULL CHECK (ordinal > 0),   -- train-wide 1..N, used for allocation
    label        VARCHAR(20) NOT NULL,
    CONSTRAINT uq_berths_coach_number UNIQUE (coach_id, berth_number)
);
CREATE INDEX idx_berths_coach_ordinal ON berths (coach_id, ordinal);

-- The hot row. The CHECK constraints make a negative or over-capacity counter impossible
-- even if application code is wrong.
CREATE TABLE quota_inventory (
    id                BIGSERIAL   PRIMARY KEY,
    train_schedule_id BIGINT      NOT NULL REFERENCES train_schedules (id),
    quota_type        VARCHAR(16) NOT NULL CHECK (quota_type IN ('TATKAL', 'GENERAL')),
    total_capacity    INT         NOT NULL CHECK (total_capacity > 0),
    available_count   INT         NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_quota_schedule_type UNIQUE (train_schedule_id, quota_type),
    CONSTRAINT ck_quota_available CHECK (available_count >= 0 AND available_count <= total_capacity)
);

CREATE TABLE rail_bookings (
    id                BIGSERIAL    PRIMARY KEY,
    public_id         VARCHAR(40)  NOT NULL UNIQUE,
    pnr               VARCHAR(12)  NOT NULL UNIQUE,
    user_id           VARCHAR(64)  NOT NULL,
    train_schedule_id BIGINT       NOT NULL REFERENCES train_schedules (id),
    quota_type        VARCHAR(16)  NOT NULL,
    passenger_count   INT          NOT NULL CHECK (passenger_count BETWEEN 1 AND 6),
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_rail_bookings_user ON rail_bookings (user_id, created_at DESC);

CREATE TABLE rail_passengers (
    id                BIGSERIAL    PRIMARY KEY,
    rail_booking_id   BIGINT       NOT NULL REFERENCES rail_bookings (id),
    train_schedule_id BIGINT       NOT NULL REFERENCES train_schedules (id),
    name              VARCHAR(120) NOT NULL,
    age               INT          NOT NULL CHECK (age BETWEEN 0 AND 120),
    berth_id          BIGINT       NOT NULL REFERENCES berths (id),
    -- Duplicate allocation of one berth on one journey is impossible at the schema level.
    CONSTRAINT uq_passenger_berth UNIQUE (train_schedule_id, berth_id)
);
CREATE INDEX idx_rail_passengers_booking ON rail_passengers (rail_booking_id);
