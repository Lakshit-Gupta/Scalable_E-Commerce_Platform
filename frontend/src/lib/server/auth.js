import { env } from '$env/dynamic/private';

/**
 * Storefront auth (v0.1.0). Server-side only. The SvelteKit Node server performs a Keycloak
 * password-grant login (server-to-server, browser never sees Keycloak) and stores the resulting
 * access token in an httpOnly cookie. The api-gateway remains the trust boundary — it validates the
 * token against the realm JWKS and injects X-User-Id; we only decode it here for display.
 */
const TOKEN_URL =
  env.KEYCLOAK_TOKEN_URL ||
  'http://keycloak:8080/realms/ecommerce/protocol/openid-connect/token';
const CLIENT_ID = env.KEYCLOAK_CLIENT_ID || 'ecommerce-app';

export const SESSION_COOKIE = 'access_token';

/** Password-grant login against Keycloak. Returns the token response, or null on failure. */
export async function login(fetchFn, username, password) {
  try {
    const res = await fetchFn(TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: CLIENT_ID,
        username,
        password
      })
    });
    if (!res.ok) {
      return null;
    }
    return await res.json();
  } catch {
    return null;
  }
}

/** Decode an (unverified) JWT payload for display only — never a security decision. */
export function decodeToken(token) {
  try {
    const payload = token.split('.')[1];
    const json = Buffer.from(payload, 'base64').toString('utf-8');
    return JSON.parse(json);
  } catch {
    return null;
  }
}
