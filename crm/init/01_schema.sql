-- CRM BionicPRO: клиенты и их протезы.
-- В продуктовом контуре это Битрикс24, для стенда — PostgreSQL с той же структурой.

CREATE TABLE IF NOT EXISTS clients (
    id               BIGSERIAL PRIMARY KEY,
    login            VARCHAR(64)  NOT NULL UNIQUE,
    full_name        VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    prosthesis_model VARCHAR(64),
    country          VARCHAR(64),
    city             VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO clients (login, full_name, email, prosthesis_model, country, city) VALUES
    ('user1',       'Иван Петров',      'user1@example.com',       'BionicPRO Hand S2', 'Россия',   'Москва'),
    ('user2',       'Мария Соколова',   'user2@example.com',       'BionicPRO Hand S2', 'Россия',   'Казань'),
    ('prothetic1',  'Алексей Кузнецов', 'prothetic1@example.com',  'BionicPRO Arm X1',  'Россия',   'Москва'),
    ('prothetic2',  'Ольга Смирнова',   'prothetic2@example.com',  'BionicPRO Arm X1',  'Россия',   'Новосибирск'),
    ('prothetic3',  'Дмитрий Волков',   'prothetic3@example.com',  'BionicPRO Leg M1',  'Россия',   'Сочи'),
    ('john.doe',    'John Doe',         'john@example.com',        'BionicPRO Arm X1',  'Германия', 'Берлин'),
    ('jane.smith',  'Jane Smith',       'jane@example.com',        'BionicPRO Hand S2', 'Германия', 'Мюнхен'),
    ('alex.johnson','Alex Johnson',     'alex@example.com',        'BionicPRO Leg M1',  'Германия', 'Гамбург')
ON CONFLICT (login) DO NOTHING;
