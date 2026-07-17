CREATE TABLE IF NOT EXISTS products (
    id              UUID          PRIMARY KEY,
    name            VARCHAR(255),
    description     TEXT,
    category        VARCHAR(255),
    brand           VARCHAR(255),
    price           NUMERIC(19, 2),
    stock_quantity  INT,
    average_rating  FLOAT
);

CREATE TABLE IF NOT EXISTS product_media (
    id            UUID         PRIMARY KEY,
    product_id    VARCHAR(255),
    object_key    VARCHAR(255),
    content_type  VARCHAR(255),
    created_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_product_media_product_id ON product_media (product_id);
