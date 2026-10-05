CREATE TABLE IF NOT EXISTS user_profile (
    id                VARCHAR(64) PRIMARY KEY,
    username          VARCHAR(255),
    email             VARCHAR(255),
    first_name        VARCHAR(255),
    last_name         VARCHAR(255),
    phone             VARCHAR(64),
    identity_provider VARCHAR(64),
    yandex_id         VARCHAR(64),
    yandex_login      VARCHAR(255),
    avatar_url        VARCHAR(512),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
