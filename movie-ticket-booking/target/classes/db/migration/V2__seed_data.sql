-- ============================================================
-- Seed data — local development and integration testing
-- ============================================================

-- ── Theatre partners ──────────────────────────────────────────────────────────

INSERT INTO theatre_partner (id, business_name, gstin, contact_email, contact_phone)
VALUES
  ('a1000000-0000-0000-0000-000000000001', 'PVR Cinemas Ltd',       '29AABCP1234A1Z5', 'partnerships@pvr.in',     '+91-9900001001'),
  ('a1000000-0000-0000-0000-000000000002', 'INOX Leisure Ltd',      '27AABCI5678B1Z3', 'partnerships@inox.com',   '+91-9900001002');

-- ── Theatres (2 in Bangalore, 2 in Mumbai) ────────────────────────────────────

INSERT INTO theatre (id, partner_id, name, city, state, address, pincode, latitude, longitude)
VALUES
  ('b1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
   'PVR Forum Mall',       'Bangalore', 'Karnataka', 'Forum Value Mall, ITPL Main Rd, Whitefield', '560066', 12.980801, 77.727150),
  ('b1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
   'PVR Orion Mall',       'Bangalore', 'Karnataka', 'Orion Mall, Dr Rajkumar Rd, Rajajinagar',    '560010', 13.011942, 77.554882),
  ('b1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000002',
   'INOX R-City Mall',     'Mumbai',    'Maharashtra', 'R City Mall, LBS Marg, Ghatkopar West',    '400086', 19.086481, 72.908958),
  ('b1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000002',
   'INOX Nariman Point',   'Mumbai',    'Maharashtra', 'Marine Lines, Nariman Point',              '400021', 18.925550, 72.823210);

-- ── Screens — 2 per theatre (one 2D, one 3D) ─────────────────────────────────

INSERT INTO screen (id, theatre_id, name, screen_type, total_seats)
VALUES
  -- PVR Forum Mall
  ('c1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Screen 1', '2D',   100),
  ('c1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Screen 2', '3D',   100),
  -- PVR Orion Mall
  ('c1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000002', 'Screen 1', '2D',   100),
  ('c1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000002', 'Screen 2', '3D',   100),
  -- INOX R-City Mall
  ('c1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000003', 'Screen 1', '2D',   100),
  ('c1000000-0000-0000-0000-000000000006', 'b1000000-0000-0000-0000-000000000003', 'Screen 2', '3D',   100),
  -- INOX Nariman Point
  ('c1000000-0000-0000-0000-000000000007', 'b1000000-0000-0000-0000-000000000004', 'Screen 1', '2D',   100),
  ('c1000000-0000-0000-0000-000000000008', 'b1000000-0000-0000-0000-000000000004', 'Screen 2', '3D',   100);

-- ── Seat layout per screen — rows A–J, seats 1–10 ────────────────────────────
-- Category assignment: rows A-D = REGULAR, E-G = PREMIUM, H-J = RECLINER
-- Generated via cross-join of screen list × row letters × seat numbers.

INSERT INTO seat (id, screen_id, row_label, seat_number, category)
SELECT
    gen_random_uuid(),
    s.id                                                         AS screen_id,
    chr(64 + r.row_num)                                          AS row_label,
    n.seat_num                                                   AS seat_number,
    CASE
        WHEN r.row_num <= 4 THEN 'REGULAR'
        WHEN r.row_num <= 7 THEN 'PREMIUM'
        ELSE                     'RECLINER'
    END                                                          AS category
FROM screen s
CROSS JOIN generate_series(1, 10) AS r(row_num)
CROSS JOIN generate_series(1, 10) AS n(seat_num)
WHERE s.id IN (
    'c1000000-0000-0000-0000-000000000001',
    'c1000000-0000-0000-0000-000000000002',
    'c1000000-0000-0000-0000-000000000003',
    'c1000000-0000-0000-0000-000000000004',
    'c1000000-0000-0000-0000-000000000005',
    'c1000000-0000-0000-0000-000000000006',
    'c1000000-0000-0000-0000-000000000007',
    'c1000000-0000-0000-0000-000000000008'
);

-- ── Movies ────────────────────────────────────────────────────────────────────

INSERT INTO movie (id, title, description, language, genre, duration_min, rating, imdb_score, poster_url, release_date)
VALUES
  ('d1000000-0000-0000-0000-000000000001',
   'Kalki 2898 AD',
   'A futuristic sci-fi epic set in the year 2898, inspired by Hindu mythology.',
   'Telugu', 'Action', 180, 'UA', 7.6,
   'https://assets.example.com/posters/kalki2898.jpg',
   '2024-06-27'),

  ('d1000000-0000-0000-0000-000000000002',
   'Stree 2',
   'The residents of Chanderi face a terrifying new supernatural threat.',
   'Hindi', 'Horror Comedy', 145, 'UA', 8.2,
   'https://assets.example.com/posters/stree2.jpg',
   '2024-08-15'),

  ('d1000000-0000-0000-0000-000000000003',
   'The Sabarmati Report',
   'A journalist digs into the truth behind a controversial train fire incident.',
   'Hindi', 'Thriller', 135, 'U', 7.4,
   'https://assets.example.com/posters/sabarmati.jpg',
   '2024-11-15'),

  ('d1000000-0000-0000-0000-000000000004',
   'Amaran',
   'Based on the true story of Major Mukund Varadarajan, a decorated Indian Army officer.',
   'Tamil', 'Biography', 166, 'UA', 8.5,
   'https://assets.example.com/posters/amaran.jpg',
   '2024-10-31'),

  ('d1000000-0000-0000-0000-000000000005',
   'Pushpa 2: The Rule',
   'Pushpa Raj expands his red sandalwood smuggling empire while defying a ruthless officer.',
   'Telugu', 'Action', 210, 'A', 7.8,
   'https://assets.example.com/posters/pushpa2.jpg',
   '2024-12-05');

-- ── Shows — 4 slots × 7 days × 2 screens per city for the top 3 movies ───────
-- Slot boundaries: MORNING=09:00, AFTERNOON=13:00, EVENING=18:00, NIGHT=21:30
-- Prices: 2D MORNING=120, 2D AFTERNOON=150, 2D EVENING=200, 2D NIGHT=200
--         3D surcharge: +50 per ticket stored at show level via base_price

DO $$
DECLARE
    day_offset  INT;
    show_date   DATE;
BEGIN
    FOR day_offset IN 0..6 LOOP
        show_date := CURRENT_DATE + day_offset;

        -- PVR Forum Mall — Screen 1 (2D) — Kalki 2898 AD
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001',
           show_date, '09:00', '12:00', 'MORNING',   120.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001',
           show_date, '13:00', '16:00', 'AFTERNOON', 150.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001',
           show_date, '18:00', '21:00', 'EVENING',   200.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001',
           show_date, '21:30', '00:30', 'NIGHT',     200.00, 'SCHEDULED');

        -- PVR Forum Mall — Screen 2 (3D) — Stree 2
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002',
           show_date, '09:30', '12:00', 'MORNING',   170.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002',
           show_date, '13:30', '16:00', 'AFTERNOON', 200.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002',
           show_date, '18:30', '21:00', 'EVENING',   250.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002',
           show_date, '21:45', '00:15', 'NIGHT',     250.00, 'SCHEDULED');

        -- PVR Orion Mall — Screen 1 (2D) — Amaran
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000003',
           show_date, '10:00', '12:46', 'MORNING',   130.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000003',
           show_date, '14:00', '16:46', 'AFTERNOON', 160.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000003',
           show_date, '18:00', '20:46', 'EVENING',   210.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000003',
           show_date, '21:30', '00:16', 'NIGHT',     210.00, 'SCHEDULED');

        -- PVR Orion Mall — Screen 2 (3D) — Pushpa 2
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000004',
           show_date, '09:00', '12:30', 'MORNING',   180.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000004',
           show_date, '13:30', '17:00', 'AFTERNOON', 210.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000004',
           show_date, '18:00', '21:30', 'EVENING',   260.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000005', 'c1000000-0000-0000-0000-000000000004',
           show_date, '22:00', '01:30', 'NIGHT',     260.00, 'SCHEDULED');

        -- INOX R-City — Screen 1 (2D) — The Sabarmati Report
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000005',
           show_date, '10:30', '13:00', 'MORNING',   120.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000005',
           show_date, '14:00', '16:30', 'AFTERNOON', 150.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000005',
           show_date, '17:30', '20:00', 'EVENING',   200.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000005',
           show_date, '21:00', '23:30', 'NIGHT',     200.00, 'SCHEDULED');

        -- INOX R-City — Screen 2 (3D) — Kalki 2898 AD
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000006',
           show_date, '09:30', '12:30', 'MORNING',   170.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000006',
           show_date, '13:00', '16:00', 'AFTERNOON', 200.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000006',
           show_date, '18:00', '21:00', 'EVENING',   250.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000006',
           show_date, '21:30', '00:30', 'NIGHT',     250.00, 'SCHEDULED');

        -- INOX Nariman Point — Screen 1 (2D) — Stree 2
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000007',
           show_date, '10:00', '12:25', 'MORNING',   130.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000007',
           show_date, '14:00', '16:25', 'AFTERNOON', 160.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000007',
           show_date, '18:00', '20:25', 'EVENING',   210.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000007',
           show_date, '21:30', '23:55', 'NIGHT',     210.00, 'SCHEDULED');

        -- INOX Nariman Point — Screen 2 (3D) — Amaran
        INSERT INTO show (id, movie_id, screen_id, show_date, start_time, end_time, slot, base_price, status)
        VALUES
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000008',
           show_date, '09:00', '11:46', 'MORNING',   180.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000008',
           show_date, '13:00', '15:46', 'AFTERNOON', 210.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000008',
           show_date, '18:00', '20:46', 'EVENING',   260.00, 'SCHEDULED'),
          (gen_random_uuid(), 'd1000000-0000-0000-0000-000000000004', 'c1000000-0000-0000-0000-000000000008',
           show_date, '21:30', '00:16', 'NIGHT',     260.00, 'SCHEDULED');

    END LOOP;
END $$;

-- ── Seat inventory — one row per (show × seat), cross-join ───────────────────
-- Price per inventory row inherits from the show's base_price.
-- Category surcharge: PREMIUM +30%, RECLINER +60%.

INSERT INTO seat_inventory (id, show_id, seat_id, price, status)
SELECT
    gen_random_uuid(),
    sh.id                                  AS show_id,
    se.id                                  AS seat_id,
    ROUND(
        sh.base_price * CASE se.category
            WHEN 'PREMIUM'  THEN 1.30
            WHEN 'RECLINER' THEN 1.60
            ELSE                 1.00
        END,
    2)                                     AS price,
    'AVAILABLE'                            AS status
FROM show sh
JOIN seat se ON se.screen_id = sh.screen_id;

-- ── Customers ─────────────────────────────────────────────────────────────────

INSERT INTO customer (id, name, email, phone, country_code)
VALUES
  ('e1000000-0000-0000-0000-000000000001', 'Aditya Kumar',  'aditya@example.com', '+91-9876543210', 'IN'),
  ('e1000000-0000-0000-0000-000000000002', 'Priya Sharma',  'priya@example.com',  '+91-9876543211', 'IN');
