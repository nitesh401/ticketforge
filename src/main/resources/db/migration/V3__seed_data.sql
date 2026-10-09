INSERT INTO users (id, display_name, role) VALUES
    ('user-101', 'User A', 'USER'),
    ('user-102', 'User B', 'USER'),
    ('admin-1',  'Admin',  'ADMIN');

INSERT INTO movies (title, duration_minutes) VALUES ('Avengers', 150);
INSERT INTO theatres (name, city) VALUES ('PVR Hyderabad', 'Hyderabad');
INSERT INTO screens (theatre_id, code, name)
    SELECT id, 'AUDITORIUM-01', 'Screen 3' FROM theatres WHERE name = 'PVR Hyderabad';

INSERT INTO seats (screen_id, label)
    SELECT s.id, r.row_label || n.num
      FROM screens s
     CROSS JOIN (VALUES ('A'), ('B'), ('C')) AS r(row_label)
     CROSS JOIN generate_series(1, 4) AS n(num)
     WHERE s.code = 'AUDITORIUM-01';

-- Two shows on the same screen: same physical seats, independent inventory.
INSERT INTO shows (public_id, movie_id, screen_id, starts_at, price_minor)
    SELECT 'SHOW-AVENGERS-1930', m.id, sc.id, TIMESTAMPTZ '2026-10-15 19:30:00+05:30', 25000
      FROM movies m, screens sc WHERE m.title = 'Avengers' AND sc.code = 'AUDITORIUM-01';
INSERT INTO shows (public_id, movie_id, screen_id, starts_at, price_minor)
    SELECT 'SHOW-AVENGERS-2230', m.id, sc.id, TIMESTAMPTZ '2026-10-15 22:30:00+05:30', 25000
      FROM movies m, screens sc WHERE m.title = 'Avengers' AND sc.code = 'AUDITORIUM-01';

INSERT INTO show_seats (show_id, seat_id)
    SELECT sh.id, se.id FROM shows sh JOIN seats se ON se.screen_id = sh.screen_id;

-- Railway demo: train 12951, Tatkal quota of 100 berths.
INSERT INTO trains (train_no, name, source_station, destination_station)
    VALUES ('12951', 'Demo Rajdhani Express', 'Delhi', 'Mumbai');
INSERT INTO train_schedules (train_id, journey_date, departure_at)
    SELECT id, DATE '2026-11-15', TIMESTAMPTZ '2026-11-15 16:55:00+05:30' FROM trains WHERE train_no = '12951';
INSERT INTO coaches (train_id, code)
    SELECT t.id, 'S' || g.i FROM trains t CROSS JOIN generate_series(1, 5) AS g(i) WHERE t.train_no = '12951';
INSERT INTO berths (coach_id, berth_number, ordinal, label)
    SELECT c.id, n.num, (substring(c.code FROM 2)::int - 1) * 20 + n.num, c.code || '-' || n.num
      FROM coaches c CROSS JOIN generate_series(1, 20) AS n(num);
INSERT INTO quota_inventory (train_schedule_id, quota_type, total_capacity, available_count)
    SELECT id, 'TATKAL', 100, 100 FROM train_schedules;
