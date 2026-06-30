import { getJson } from '$lib/server/gateway';

/** Home page (SSR): trending recommendations + a sample of the catalog, fetched via the gateway. */
export async function load({ fetch }) {
  const [trending, products] = await Promise.all([
    getJson(fetch, '/api/recommendations/trending?limit=5', []),
    getJson(fetch, '/api/products/search?q=a', [])
  ]);
  return { trending, products };
}
