<script>
  import { browser } from '$app/environment';
  import { onMount } from 'svelte';
  import { afterNavigate } from '$app/navigation';
  import { env } from '$env/dynamic/public';
  import { initPostHog } from '$lib/posthog';
  import { initChatwoot, identifyChatwootUser, resetChatwootUser } from '$lib/chatwoot';

  let { children, data } = $props();

  let theme = $state('light');

  // Product analytics (v0.0.17): init PostHog client-side only when a key is configured (else no-op).
  let posthog = null;
  onMount(() => {
    theme = document.documentElement.dataset.theme || 'light';
    posthog = initPostHog(env.PUBLIC_POSTHOG_KEY, env.PUBLIC_POSTHOG_HOST);
    if (posthog) posthog.capture('$pageview');
    // Live-chat support (v0.0.18): load the Chatwoot widget when configured (else no-op).
    initChatwoot(env.PUBLIC_CHATWOOT_BASE_URL, env.PUBLIC_CHATWOOT_WEBSITE_TOKEN);
  });
  afterNavigate(() => {
    if (browser && posthog) posthog.capture('$pageview');
  });

  // Live-chat identity (v0.1.2): identify the logged-in user to Chatwoot (server-signed hash),
  // or reset to anonymous on logout. Re-runs when the session changes across navigations.
  $effect(() => {
    if (!browser) return;
    if (data?.chatwoot) {
      identifyChatwootUser(data.chatwoot);
    } else {
      resetChatwootUser();
    }
  });

  function toggleTheme() {
    theme = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('theme', theme);
    } catch (e) {}
  }
</script>

<header>
  <div class="bar">
    <a href="/" class="brand">
      <span class="mark" aria-hidden="true">S</span>ShopSphere
    </a>

    <form method="GET" action="/search" class="search" role="search">
      <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"
        ><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg
      >
      <input name="q" placeholder="Search products, brands, categories…" aria-label="Search" />
    </form>

    <nav>
      <a href="/blog" class="nav-link">Blog</a>
      <button class="icon-btn" onclick={toggleTheme} aria-label="Toggle theme" title="Toggle theme">
        {#if theme === 'dark'}
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5" /></svg>
        {:else}
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /></svg>
        {/if}
      </button>

      <a href="/cart" class="cart" aria-label="Cart">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" /><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6" /></svg>
        <span>Cart</span>
      </a>

      {#if data?.user}
        <span class="who">Hi, {data.user.username}</span>
        <form method="POST" action="/logout" class="inline">
          <button type="submit" class="link">Logout</button>
        </form>
      {:else}
        <a href="/login" class="login">Log in</a>
      {/if}
    </nav>
  </div>
  <div class="awning" aria-hidden="true"></div>
</header>

<main>
  {@render children()}
</main>

<footer>
  <span class="mark small" aria-hidden="true">S</span>
  ShopSphere · SvelteKit SSR storefront → api-gateway over REST
</footer>

<style>
  /* ---------- Design tokens · "The Modern Bazaar" ---------- */
  :global(:root) {
    --bg: #ecf1e2;            /* pista paper */
    --surface: #f8faf1;
    --surface-2: #e2e9d1;
    --ink: #22382c;           /* cardamom */
    --ink-soft: #3d5346;
    --muted: #68796b;
    --border: #cdd8ba;
    --accent: #c22a66;        /* rani pink */
    --accent-ink: #fff6ef;
    --accent-soft: #f3dbe5;
    --teal: #175e52;          /* peacock */
    --teal-soft: #d7e6dc;
    --gold: #de9a26;          /* marigold */
    --gold-soft: #f6e7c6;
    --ok: #2e7d46;
    --ok-soft: #dfedd9;
    --err: #b3261e;
    --err-soft: #f7dfda;
    --warn: #b0761a;
    --shadow: 0 1px 2px rgba(34, 56, 44, 0.05), 0 10px 28px rgba(34, 56, 44, 0.08);
    --shadow-hover: 0 2px 4px rgba(34, 56, 44, 0.06), 0 18px 44px rgba(34, 56, 44, 0.14);
    --radius: 20px;
    --radius-sm: 12px;
    --maxw: 1140px;
    --disp: 'Bricolage Grotesque', 'Hanken Grotesk', system-ui, sans-serif;
    --mono: 'Spline Sans Mono', ui-monospace, monospace;
  }
  :global([data-theme='dark']) {
    /* night market */
    --bg: #0f1d17;
    --surface: #17281f;
    --surface-2: #1f3326;
    --ink: #eaf1df;
    --ink-soft: #c8d6be;
    --muted: #8fa391;
    --border: #2c4433;
    --accent: #e75a94;
    --accent-ink: #2a0a18;
    --accent-soft: #3a1f2c;
    --teal: #3aa08c;
    --teal-soft: #1c3a31;
    --gold: #e9b44c;
    --gold-soft: #3a2f16;
    --ok: #6fbf7f;
    --ok-soft: #1c3325;
    --err: #f08578;
    --err-soft: #3a1f1c;
    --warn: #e9b44c;
    --shadow: 0 1px 2px rgba(0, 0, 0, 0.3), 0 10px 28px rgba(0, 0, 0, 0.4);
    --shadow-hover: 0 2px 4px rgba(0, 0, 0, 0.35), 0 18px 44px rgba(0, 0, 0, 0.55);
  }

  :global(*) { box-sizing: border-box; }
  :global(html) { scroll-behavior: smooth; }
  :global(body) {
    font-family: 'Hanken Grotesk', system-ui, -apple-system, 'Segoe UI', sans-serif;
    margin: 0;
    color: var(--ink);
    background: var(--bg);
    -webkit-font-smoothing: antialiased;
    transition: background 0.2s ease, color 0.2s ease;
  }
  :global(h1) {
    font-family: var(--disp);
    font-size: 2rem; font-weight: 700; line-height: 1.1;
    margin: 0 0 0.5rem; letter-spacing: -0.015em;
  }
  :global(h2) {
    font-family: var(--disp);
    font-size: 1.35rem; font-weight: 650; margin: 0 0 1.1rem; letter-spacing: -0.01em;
    display: flex; align-items: center; gap: 0.5rem;
  }
  :global(.serif) { font-family: var(--disp); }
  :global(.muted) { color: var(--muted); }
  :global(.mono) { font-family: var(--mono); }
  :global(section) { margin-bottom: 3rem; }
  :global(a) { color: var(--teal); }
  /* shared responsive card grid used by home / search / product pages */
  :global(.grid) {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 1.25rem;
  }
  :global(.btn) {
    display: inline-flex; align-items: center; justify-content: center; gap: 0.5rem;
    padding: 0.68rem 1.2rem; border: 0; border-radius: var(--radius-sm);
    background: var(--accent); color: var(--accent-ink); cursor: pointer;
    font: inherit; font-weight: 700; font-size: 0.9rem; letter-spacing: 0.01em;
    box-shadow: 0 2px 0 color-mix(in srgb, var(--accent) 55%, var(--ink));
    transition: transform 0.12s ease, box-shadow 0.12s ease, background 0.12s ease;
  }
  :global(.btn:hover) { transform: translateY(-1px); }
  :global(.btn:active) { transform: translateY(1px); box-shadow: 0 0 0 transparent; }
  :global(.btn.ghost) {
    background: transparent; color: var(--ink); border: 1.5px solid var(--ink-soft); box-shadow: none;
  }
  :global(.btn.ghost:hover) { border-color: var(--accent); color: var(--accent); }
  :global(:focus-visible) { outline: 2.5px solid var(--teal); outline-offset: 2px; border-radius: 4px; }

  /* ---------- Header ---------- */
  header {
    background: var(--surface);
    position: sticky; top: 0; z-index: 20;
  }
  /* signature: market-stall awning — striped band + scalloped fringe */
  .awning {
    height: 16px;
    background-image:
      repeating-linear-gradient(90deg, var(--accent) 0 28px, var(--gold) 28px 56px),
      radial-gradient(circle 13px at 14px 0, var(--accent) 12px, transparent 13px),
      radial-gradient(circle 13px at 14px 0, var(--gold) 12px, transparent 13px);
    background-size: 100% 7px, 56px 9px, 56px 9px;
    background-position: 0 0, 0 7px, 28px 7px;
    background-repeat: repeat-x;
  }
  .bar {
    display: flex; gap: 1rem; align-items: center;
    max-width: var(--maxw); margin: 0 auto; padding: 0.8rem 1.5rem;
  }
  .brand {
    display: inline-flex; align-items: center; gap: 0.55rem;
    color: var(--ink); text-decoration: none;
    font-family: var(--disp); font-weight: 800;
    font-size: 1.2rem; letter-spacing: -0.02em; white-space: nowrap;
  }
  .mark {
    display: grid; place-items: center;
    width: 26px; height: 26px;
    border-radius: 50% 50% 50% 6px;   /* hanging price-tag shape */
    background: var(--teal); color: var(--surface);
    font-family: var(--disp); font-weight: 800; font-size: 0.95rem;
    transform: rotate(-8deg);
  }
  .search {
    display: flex; align-items: center; gap: 0.5rem; flex: 1; max-width: 520px; margin: 0 auto;
    background: var(--bg); border: 1.5px solid var(--border);
    border-radius: 999px; padding: 0.15rem 0.9rem;
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
  }
  .search:focus-within { border-color: var(--teal); box-shadow: 0 0 0 4px var(--teal-soft); }
  .search .ico { width: 18px; height: 18px; flex: none; stroke: var(--muted); fill: none; stroke-width: 2; }
  .search input {
    flex: 1; padding: 0.5rem 0; border: 0; background: transparent;
    font: inherit; font-size: 0.9rem; color: var(--ink); outline: none;
  }
  nav { display: flex; gap: 0.85rem; align-items: center; white-space: nowrap; }
  nav a { color: var(--ink); text-decoration: none; font-weight: 600; font-size: 0.9rem; }
  nav a:hover { color: var(--accent); }
  .cart { display: inline-flex; align-items: center; gap: 0.4rem; }
  .cart svg { width: 18px; height: 18px; stroke: currentColor; fill: none; stroke-width: 1.8; }
  .icon-btn {
    display: inline-flex; align-items: center; justify-content: center;
    width: 36px; height: 36px; padding: 0; border: 1.5px solid var(--border);
    border-radius: 50% 50% 50% 8px; background: var(--surface); color: var(--ink-soft); cursor: pointer;
    transition: border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
  }
  .icon-btn:hover { border-color: var(--gold); color: var(--gold); transform: rotate(-12deg); }
  .icon-btn svg { width: 18px; height: 18px; stroke: currentColor; fill: none; stroke-width: 1.8; stroke-linecap: round; }
  nav .who { color: var(--muted); font-size: 0.85rem; }
  nav form.inline { margin: 0; }
  nav .link {
    background: none; border: 0; padding: 0; cursor: pointer; font: inherit;
    color: var(--muted); text-decoration: underline; font-weight: 500; font-size: 0.9rem;
  }
  nav .link:hover { color: var(--accent); }
  .login {
    padding: 0.5rem 1rem; background: var(--accent); color: var(--accent-ink) !important;
    border-radius: 999px; font-weight: 700;
  }
  .login:hover { background: color-mix(in srgb, var(--accent) 88%, var(--ink)); }

  main { max-width: var(--maxw); margin: 2.25rem auto 4rem; padding: 0 1.5rem; min-height: 60vh; }
  footer {
    display: flex; align-items: center; justify-content: center; gap: 0.55rem;
    color: var(--muted); font-size: 0.8rem; padding: 2.5rem 1.5rem;
    border-top: 1.5px dashed var(--border);
  }
  .mark.small { width: 20px; height: 20px; font-size: 0.72rem; }

  @media (max-width: 660px) {
    .bar { flex-wrap: wrap; }
    .search { order: 3; max-width: none; width: 100%; }
    nav .who { display: none; }
  }
  @media (prefers-reduced-motion: reduce) {
    :global(html) { scroll-behavior: auto; }
    :global(*), :global(*::before), :global(*::after) {
      animation-duration: 0.01ms !important; animation-iteration-count: 1 !important;
      transition-duration: 0.01ms !important;
    }
  }
</style>
