-- orders_db. Order + order_item volume. user_id/product_id reference the deterministic
-- ids seeded in users_db/products_db (string columns, cross-db-safe). Params: :orders :users :products :avg_items
\set ON_ERROR_STOP on
INSERT INTO orders (id, payment_id, status, total_amount, user_id)
SELECT
  md5('o'||g)::uuid,
  md5('pay'||g)::uuid::text,
  (ARRAY['CONFIRMED','CONFIRMED','CONFIRMED','CONFIRMED','CONFIRMED','CONFIRMED','CONFIRMED','CONFIRMED','CANCELLED','PENDING'])[1+(g % 10)],
  round((1000 + (g % 99000))::numeric / 100, 2),
  md5('u'||(1 + (g % :users)))::uuid::text
FROM generate_series(1, :orders) g;

INSERT INTO order_items (id, price, product_id, quantity)
SELECT
  md5('oi'||g)::uuid,
  round((500 + (g % 9500))::numeric / 100, 2),
  md5('p'||(1 + (g % :products)))::uuid::text,
  1 + (g % 3)
FROM generate_series(1, :orders * :avg_items) g;
