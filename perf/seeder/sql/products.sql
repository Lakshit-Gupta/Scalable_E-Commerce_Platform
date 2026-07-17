-- products_db. Realistic, searchable product rows via generate_series + a word vocab.
-- Deterministic ids (md5('p'||i)) so orders/order_items in other DBs can reference them
-- without a cross-database query. Params: :products
\set ON_ERROR_STOP on
WITH vocab AS (
  SELECT
    ARRAY['Aero','Ultra','Nova','Pulse','Vertex','Lumen','Terra','Volt','Prime','Zen','Flux','Apex','Halo','Onyx','Drift']::text[] AS adj,
    ARRAY['Headphones','Sneakers','Backpack','Watch','Speaker','Jacket','Camera','Keyboard','Bottle','Lamp','Charger','Wallet','Sunglasses','Drone','Mouse']::text[] AS noun,
    ARRAY['Electronics','Footwear','Apparel','Accessories','Home','Sports','Outdoors','Audio']::text[] AS cat,
    ARRAY['Northwind','Contoso','Acme','Fabrikam','Globex','Umbra','Vela','Kestrel']::text[] AS brand
)
INSERT INTO products (id, name, description, category, brand, price, stock_quantity, average_rating)
SELECT
  md5('p'||g)::uuid,
  v.adj[1+(g % array_length(v.adj,1))] || ' ' || v.noun[1+((g/7) % array_length(v.noun,1))],
  'Premium ' || v.noun[1+((g/7) % array_length(v.noun,1))] || ' by ' || v.brand[1+((g/3) % array_length(v.brand,1))] || ' — durable, lightweight, everyday carry.',
  v.cat[1+(g % array_length(v.cat,1))],
  v.brand[1+((g/3) % array_length(v.brand,1))],
  round((999 + (g % 90000))::numeric / 100, 2),
  200 + (g % 800),
  round((30 + (g % 20))::numeric / 10, 1)
FROM generate_series(1, :products) g, vocab v;
