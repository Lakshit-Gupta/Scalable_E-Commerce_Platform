import crypto from 'node:crypto';
import { env } from '$env/dynamic/private';
import { decodeToken } from '$lib/server/auth';

/**
 * Chatwoot identity validation (v0.1.2). Chatwoot verifies a logged-in visitor by an
 * `identifier_hash` = HMAC-SHA256(identifier, <inbox HMAC token>). The token is a server secret,
 * so the hash is computed here (never shipped to the browser) and handed to the client, which calls
 * `window.$chatwoot.setUser(identifier, { …, identifier_hash })`.
 *
 * Returns null (→ anonymous chat) when the user is logged out or `CHATWOOT_HMAC_TOKEN` is unset,
 * matching the widget's graceful no-op-without-config behaviour.
 */
export function chatwootIdentity(user) {
  const token = env.CHATWOOT_HMAC_TOKEN;
  if (!user?.token || !token) {
    return null;
  }
  const claims = decodeToken(user.token);
  const identifier = claims?.sub;
  if (!identifier) {
    return null;
  }
  const identifierHash = crypto.createHmac('sha256', token).update(identifier).digest('hex');
  return {
    identifier,
    name: claims.preferred_username ?? claims.name ?? '',
    email: claims.email ?? '',
    identifier_hash: identifierHash
  };
}
