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
