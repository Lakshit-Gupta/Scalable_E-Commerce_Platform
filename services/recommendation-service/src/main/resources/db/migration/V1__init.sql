CREATE TABLE IF NOT EXISTS user_purchases (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       VARCHAR(255) NOT NULL,
    product_id    VARCHAR(255) NOT NULL,
    purchased_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_purchases_user_id ON user_purchases (user_id);
