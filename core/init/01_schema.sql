-- Основная база BionicPRO: протезы и телеметрия с датчиков.
-- Данные приходят от программы в чипе протеза и используются для обучения модели
-- распознавания движений и для отчётов пользователю.

CREATE TABLE IF NOT EXISTS devices (
    id            BIGSERIAL PRIMARY KEY,
    login         VARCHAR(64) NOT NULL,
    serial_number VARCHAR(64) NOT NULL UNIQUE,
    model         VARCHAR(64),
    activated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS telemetry_events (
    id            BIGSERIAL PRIMARY KEY,
    device_serial VARCHAR(64)  NOT NULL,
    login         VARCHAR(64)  NOT NULL,
    event_time    TIMESTAMPTZ  NOT NULL,
    movement      VARCHAR(32),
    response_ms   INTEGER,
    battery_level SMALLINT,
    error_code    VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS telemetry_events_login_time_idx
    ON telemetry_events (login, event_time);

INSERT INTO devices (login, serial_number, model) VALUES
    ('user1',        'BP-0001', 'BionicPRO Hand S2'),
    ('user2',        'BP-0002', 'BionicPRO Hand S2'),
    ('prothetic1',   'BP-0003', 'BionicPRO Arm X1'),
    ('prothetic2',   'BP-0004', 'BionicPRO Arm X1'),
    ('prothetic3',   'BP-0005', 'BionicPRO Leg M1'),
    ('john.doe',     'BP-0006', 'BionicPRO Arm X1'),
    ('jane.smith',   'BP-0007', 'BionicPRO Hand S2'),
    ('alex.johnson', 'BP-0008', 'BionicPRO Leg M1')
ON CONFLICT (serial_number) DO NOTHING;

-- Тестовый поток телеметрии за последние 30 дней: часовая гранулярность.
INSERT INTO telemetry_events (device_serial, login, event_time, movement, response_ms, battery_level, error_code)
SELECT d.serial_number,
       d.login,
       now() - (g || ' hours')::interval,
       (ARRAY['сгибание', 'разгибание', 'захват', 'удержание', 'поворот'])[1 + floor(random() * 5)::int],
       (55 + floor(random() * 80))::int,
       (100 - (g % 70))::smallint,
       CASE
           WHEN random() < 0.02 THEN 'E_SENSOR'
           WHEN random() < 0.04 THEN 'E_BATTERY'
           ELSE NULL
       END
FROM devices d
CROSS JOIN generate_series(1, 720) AS g
WHERE NOT EXISTS (SELECT 1 FROM telemetry_events);
