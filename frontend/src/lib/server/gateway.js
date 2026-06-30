import { env } from '$env/dynamic/private';

/**
 * BFF helper (v0.0.16): SvelteKit server load functions call the api-gateway server-to-server.
 * The browser never talks to the gateway directly — only this Node server does.
 */
export const GATEWAY_URL = env.GATEWAY_URL || 'http://api-gateway:8080';

/** GET a gateway JSON endpoint, returning a fallback on any error so a page never hard-fails. */
export async function getJson(fetchFn, path, fallback) {
  try {
    const res = await fetchFn(`${GATEWAY_URL}${path}`);
    if (!res.ok) {
      return fallback;
    }
    return await res.json();
  } catch {
    return fallback;
  }
}

/**
 * Call a gateway endpoint with a Bearer access token (authed BFF calls — cart, orders, v0.1.0).
 * Returns the raw Response so callers can inspect status; the token comes from the session cookie.
 */
export function gatewayFetch(fetchFn, path, token, init = {}) {
  const headers = { ...(init.headers || {}) };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return fetchFn(`${GATEWAY_URL}${path}`, { ...init, headers });
}
