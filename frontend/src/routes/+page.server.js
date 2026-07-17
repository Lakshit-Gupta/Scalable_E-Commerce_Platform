import { getJson } from '$lib/server/gateway';

/** Home page (SSR): trending recommendations + a sample of the catalog, fetched via the gateway. */
export async function load({ fetch }) {
  const [trending, products] = await Promise.all([
    getJson(fetch, '/api/recommendations/trending?limit=5', []),
    // no q -> Elasticsearch match_all = browse the whole catalog
    getJson(fetch, '/api/products/search?size=60', [])
  ]);
  return { trending, products };
}
