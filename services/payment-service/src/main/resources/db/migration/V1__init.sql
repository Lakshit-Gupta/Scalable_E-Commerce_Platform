CREATE TABLE IF NOT EXISTS payments (
    id            UUID         PRIMARY KEY,
    order_id      VARCHAR(255) NOT NULL UNIQUE,
    user_id       VARCHAR(255),
    amount        NUMERIC(19, 2),
    status        VARCHAR(50),
    provider      VARCHAR(50),
    provider_ref  VARCHAR(255),
    created_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS processed_webhook_events (
    event_id      VARCHAR(255) PRIMARY KEY,
    type          VARCHAR(255),
    processed_at  TIMESTAMPTZ
);
