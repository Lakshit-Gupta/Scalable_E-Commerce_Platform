-- Database-per-service. Postgres runs this once on first init as superuser 'ecommerce'.
-- Each service connects only to its own DB (see each service's application.yml).
CREATE DATABASE users_db;
CREATE DATABASE products_db;
CREATE DATABASE orders_db;
CREATE DATABASE payments_db;
CREATE DATABASE recommendations_db;
CREATE DATABASE cms_db;           -- Payload CMS (v0.1.1)
