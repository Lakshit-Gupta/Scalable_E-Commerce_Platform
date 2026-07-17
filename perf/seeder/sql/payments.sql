-- payments_db. One payment per order (order_id references orders_db deterministic id).
-- Params: :orders :users
\set ON_ERROR_STOP on
INSERT INTO payments (id, amount, created_at, order_id, provider, provider_ref, status, user_id)
SELECT
  md5('pay'||g)::uuid,
  round((1000 + (g % 99000))::numeric / 100, 2),
  now() - ((g % 730) || ' days')::interval,
  md5('o'||g)::uuid::text,
  'stub',
  'seed_ref_'||g,
  (ARRAY['SUCCESS','SUCCESS','SUCCESS','SUCCESS','SUCCESS','SUCCESS','SUCCESS','SUCCESS','FAILED','PENDING'])[1+(g % 10)],
  md5('u'||(1 + (g % :users)))::uuid::text
FROM generate_series(1, :orders) g;
