import { chatwootIdentity } from '$lib/server/chatwoot';

/**
 * Root layout load (v0.1.0): expose the (display-only) session user to every page so the header can
 * show login/logout and the cart link. Never send the raw token to the browser.
 * v0.1.2: also expose the Chatwoot identity (identifier + server-computed identifier_hash) so the
 * live-chat widget can recognise the logged-in user. Null when logged out / HMAC token unset.
 */
export function load({ locals }) {
  return {
    user: locals.user ? { username: locals.user.username } : null,
    chatwoot: chatwootIdentity(locals.user)
  };
}
