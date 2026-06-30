import { getJson } from '$lib/server/gateway';

/** Search page (SSR): proxies the query to the gateway product search. */
export async function load({ fetch, url }) {
  const q = url.searchParams.get('q') ?? '';
  const results = q ? await getJson(fetch, `/api/products/search?q=${encodeURIComponent(q)}`, []) : [];
  return { q, results };
}
