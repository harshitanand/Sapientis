-- ============================================================
-- Online Movie Ticket Booking Platform — Initial Schema
-- ============================================================

-- Theatre partners (B2B)
CREATE TABLE theatre_partner (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name VARCHAR(200) NOT NULL,
    gstin         VARCHAR(20)  UNIQUE,
    contact_email VARCHAR(150) NOT NULL,
    contact_phone VARCHAR(20)  NOT NULL,
    onboarded_at  TIMESTAMP    NOT NULL DEFAULT now(),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Physical theatres
CREATE TABLE theatre (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id  UUID         NOT NULL REFERENCES theatre_partner(id),
    name        VARCHAR(200) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100),
    country     VARCHAR(100) NOT NULL DEFAULT 'IN',
    address     TEXT,
    pincode     VARCHAR(20),
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    amenities   TEXT[],                    -- e.g. PARKING, FOOD_COURT
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_theatre_city     ON theatre(city);
CREATE INDEX idx_theatre_partner  ON theatre(partner_id);

-- Screens within a theatre
CREATE TABLE screen (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    theatre_id   UUID         NOT NULL REFERENCES theatre(id),
    name         VARCHAR(50)  NOT NULL,
    screen_type  VARCHAR(20)  NOT NULL DEFAULT '2D',   -- 2D | 3D | IMAX | 4DX
    total_seats  INT          NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- Seat layout per screen
CREATE TABLE seat (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    screen_id   UUID        NOT NULL REFERENCES screen(id),
    row_label   VARCHAR(5)  NOT NULL,   -- A, B, C …
    seat_number INT         NOT NULL,
    category    VARCHAR(20) NOT NULL,   -- REGULAR | PREMIUM | RECLINER
    UNIQUE(screen_id, row_label, seat_number)
);

CREATE INDEX idx_seat_screen ON seat(screen_id);

-- Movie catalogue
CREATE TABLE movie (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(300) NOT NULL,
    description  TEXT,
    language     VARCHAR(50)  NOT NULL,
    genre        VARCHAR(50)  NOT NULL,
    duration_min INT          NOT NULL,
    rating       VARCHAR(10),            -- U | UA | A
    imdb_score   NUMERIC(3,1),
    poster_url   VARCHAR(500),
    release_date DATE,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_movie_language ON movie(language);
CREATE INDEX idx_movie_genre    ON movie(genre);

-- Shows scheduled on a screen
CREATE TABLE show (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    movie_id    UUID           NOT NULL REFERENCES movie(id),
    screen_id   UUID           NOT NULL REFERENCES screen(id),
    show_date   DATE           NOT NULL,
    start_time  TIME           NOT NULL,
    end_time    TIME           NOT NULL,
    slot        VARCHAR(20)    NOT NULL,   -- MORNING | AFTERNOON | EVENING | NIGHT
    base_price  NUMERIC(10,2)  NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | CANCELLED | COMPLETED
    created_by  UUID,
    created_at  TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_show_movie_date   ON show(movie_id, show_date);
CREATE INDEX idx_show_screen_date  ON show(screen_id, show_date);
CREATE INDEX idx_show_date_status  ON show(show_date, status);

-- Seat inventory per show (materialised from screen seats when show is created)
CREATE TABLE seat_inventory (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    show_id     UUID           NOT NULL REFERENCES show(id),
    seat_id     UUID           NOT NULL REFERENCES seat(id),
    price       NUMERIC(10,2)  NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | LOCKED | BOOKED | BLOCKED
    locked_by   VARCHAR(100),               -- idempotency key of the locking request
    locked_at   TIMESTAMP,
    version     BIGINT         NOT NULL DEFAULT 0,   -- optimistic locking
    UNIQUE(show_id, seat_id)
);

CREATE INDEX idx_si_show_status ON seat_inventory(show_id, status);

-- End customers
CREATE TABLE customer (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    phone         VARCHAR(20),
    country_code  VARCHAR(5)   NOT NULL DEFAULT 'IN',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- Bookings
CREATE TABLE booking (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_ref      VARCHAR(20)    NOT NULL UNIQUE,  -- human-readable e.g. BK202401120001
    customer_id      UUID           NOT NULL REFERENCES customer(id),
    show_id          UUID           NOT NULL REFERENCES show(id),
    idempotency_key  VARCHAR(100)   NOT NULL UNIQUE,
    status           VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
        -- PENDING | AWAITING_PAYMENT | CONFIRMED | CANCELLED | EXPIRED
    total_amount     NUMERIC(10,2)  NOT NULL,
    discount_amount  NUMERIC(10,2)  NOT NULL DEFAULT 0,
    final_amount     NUMERIC(10,2)  NOT NULL,
    payment_ref      VARCHAR(100),
    expires_at       TIMESTAMP,        -- null means confirmed / cancelled
    created_at       TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_customer    ON booking(customer_id);
CREATE INDEX idx_booking_show        ON booking(show_id);
CREATE INDEX idx_booking_status      ON booking(status);
CREATE INDEX idx_booking_idempotency ON booking(idempotency_key);

-- Individual seat line-items within a booking
CREATE TABLE booking_item (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id       UUID           NOT NULL REFERENCES booking(id),
    seat_inventory_id UUID          NOT NULL REFERENCES seat_inventory(id),
    base_price       NUMERIC(10,2)  NOT NULL,
    discount_pct     NUMERIC(5,2)   NOT NULL DEFAULT 0,
    final_price      NUMERIC(10,2)  NOT NULL,
    UNIQUE(booking_id, seat_inventory_id)
);

-- Audit log (append-only)
CREATE TABLE booking_audit (
    id          BIGSERIAL   PRIMARY KEY,
    booking_id  UUID        NOT NULL REFERENCES booking(id),
    event_type  VARCHAR(50) NOT NULL,
    old_status  VARCHAR(30),
    new_status  VARCHAR(30),
    actor       VARCHAR(100),
    meta        JSONB,
    occurred_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_booking ON booking_audit(booking_id);

-- Platform offers
CREATE TABLE offer (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50)    NOT NULL UNIQUE,
    description   VARCHAR(300),
    discount_type VARCHAR(20)    NOT NULL,   -- PERCENTAGE | FLAT
    discount_value NUMERIC(10,2) NOT NULL,
    applies_to    VARCHAR(30)    NOT NULL,   -- TICKET_POSITION | SHOW_SLOT | CITY
    condition_key VARCHAR(50),              -- e.g. "3" for third ticket, "AFTERNOON" for slot
    valid_from    DATE           NOT NULL,
    valid_to      DATE,
    is_active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL DEFAULT now()
);

INSERT INTO offer (code, description, discount_type, discount_value, applies_to, condition_key, valid_from)
VALUES
  ('THIRD_TICKET_50', '50% off on the 3rd ticket in a booking', 'PERCENTAGE', 50, 'TICKET_POSITION', '3', CURRENT_DATE),
  ('AFTERNOON_20',    '20% off all tickets in afternoon shows', 'PERCENTAGE', 20, 'SHOW_SLOT',       'AFTERNOON', CURRENT_DATE);
