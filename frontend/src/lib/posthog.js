/**
 * PostHog web analytics loader (v0.0.17). Loaded client-side via the official CDN snippet (no npm
 * dependency, no SSR impact). No-op unless a key is provided. Returns the posthog instance or null.
 */
export function initPostHog(key, host) {
  if (typeof window === 'undefined' || !key) {
    return null;
  }
  const apiHost = host || 'https://us.i.posthog.com';

  // Official PostHog snippet: defines the queuing stub and async-loads array.js from the CDN.
  !(function (t, e) {
    let o, n, p, r;
    if (!e.__SV) {
      window.posthog = e;
      e._i = [];
      e.init = function (i, s, a) {
        function g(t, e) {
          const o = e.split('.');
          if (o.length === 2) {
            t = t[o[0]];
            e = o[1];
          }
          t[e] = function () {
            t.push([e].concat(Array.prototype.slice.call(arguments, 0)));
          };
        }
        p = t.createElement('script');
        p.type = 'text/javascript';
        p.async = true;
        p.src = s.api_host.replace('.i.posthog.com', '-assets.i.posthog.com') + '/static/array.js';
        r = t.getElementsByTagName('script')[0];
        r.parentNode.insertBefore(p, r);
        let u = e;
        if (a !== undefined) {
          u = e[a] = [];
        } else {
          a = 'posthog';
        }
        u.people = u.people || [];
        u.toString = function (t) {
          let e = 'posthog';
          if (a !== 'posthog') e += '.' + a;
          if (!t) e += ' (stub)';
          return e;
        };
        u.people.toString = function () {
          return u.toString(1) + '.people (stub)';
        };
        o = 'init capture register register_once unregister identify alias set_config reset group on onFeatureFlags isFeatureEnabled getFeatureFlag reloadFeatureFlags'.split(
          ' '
        );
        for (n = 0; n < o.length; n++) g(u, o[n]);
        e._i.push([i, s, a]);
      };
      e.__SV = 1;
    }
  })(document, window.posthog || []);

  window.posthog.init(key, { api_host: apiHost });
  return window.posthog;
}
