CREATE TABLE IF NOT EXISTS users (
    id             UUID         PRIMARY KEY,
    keycloak_id    VARCHAR(255) UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255),
    role           VARCHAR(50),
    created_at     TIMESTAMPTZ
);
