-- users_db. Bulk profile rows (DB-only, no Keycloak) for order FK + query realism.
-- The k6 login pool is a separate, small set of REAL Keycloak users (see seed.sh).
-- Deterministic ids: md5('u'||i). Params: :users
\set ON_ERROR_STOP on
INSERT INTO users (id, created_at, email, keycloak_id, password_hash, role)
SELECT
  md5('u'||g)::uuid,
  now() - ((g % 730) || ' days')::interval,
  'seeduser'||g||'@seed.test',
  'seed-'||g,
  NULL,
  'CUSTOMER'
FROM generate_series(1, :users) g
ON CONFLICT (id) DO NOTHING;
