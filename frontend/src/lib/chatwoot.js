/**
 * Chatwoot live-chat widget loader (v0.0.18). Loaded client-side via the official Chatwoot SDK
 * (no npm dependency, no SSR impact). No-op unless both a base URL and website token are provided,
 * so the storefront runs fine without a configured Chatwoot instance.
 */
export function initChatwoot(baseUrl, websiteToken) {
  if (typeof window === 'undefined' || !baseUrl || !websiteToken) {
    return;
  }
  // Avoid double-injecting the SDK on client-side navigations.
  if (window.$chatwoot || document.getElementById('chatwoot-sdk')) {
    return;
  }

  const g = document.createElement('script');
  const s = document.getElementsByTagName('script')[0];
  g.id = 'chatwoot-sdk';
  g.src = baseUrl.replace(/\/$/, '') + '/packs/js/sdk.js';
  g.defer = true;
  g.async = true;
  g.onload = function () {
    window.chatwootSDK.run({ websiteToken, baseUrl });
  };
  s.parentNode.insertBefore(g, s);
}

/**
 * Identify the logged-in visitor to Chatwoot (v0.1.2). `identity` carries the server-computed
 * `identifier_hash` (HMAC), so Chatwoot trusts the identifier. No-op without an identity (anonymous
 * chat) or outside the browser. Waits for the widget to be ready if the SDK hasn't finished loading.
 */
export function identifyChatwootUser(identity) {
  if (typeof window === 'undefined' || !identity) {
    return;
  }
  const apply = () => {
    try {
      window.$chatwoot.setUser(identity.identifier, {
        name: identity.name,
        email: identity.email,
        identifier_hash: identity.identifier_hash
      });
    } catch {
      /* widget not available — ignore */
    }
  };
  if (window.$chatwoot) {
    apply();
  } else {
    window.addEventListener('chatwoot:ready', apply, { once: true });
  }
}

/** Clear the Chatwoot session on logout (v0.1.2) so the next visitor starts anonymous. */
export function resetChatwootUser() {
  if (typeof window !== 'undefined' && window.$chatwoot) {
    try {
      window.$chatwoot.reset();
    } catch {
      /* ignore */
    }
  }
}
