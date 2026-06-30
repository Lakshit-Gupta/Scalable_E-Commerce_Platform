import { SESSION_COOKIE, decodeToken } from '$lib/server/auth';

/**
 * Server hook (v0.1.0): read the session cookie into event.locals.user so load functions and actions
 * can make authed gateway calls. The token's signature is validated downstream by the api-gateway;
 * here we only surface the username for the UI.
 */
export async function handle({ event, resolve }) {
  const token = event.cookies.get(SESSION_COOKIE);
  if (token) {
    const claims = decodeToken(token);
    event.locals.user = { username: claims?.preferred_username ?? claims?.sub ?? 'user', token };
  } else {
    event.locals.user = null;
  }
  return resolve(event);
}
