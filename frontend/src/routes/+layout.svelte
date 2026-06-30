<script>
  import { browser } from '$app/environment';
  import { onMount } from 'svelte';
  import { afterNavigate } from '$app/navigation';
  import { env } from '$env/dynamic/public';
  import { initPostHog } from '$lib/posthog';
  import { initChatwoot } from '$lib/chatwoot';

  let { children, data } = $props();

  // Product analytics (v0.0.17): init PostHog client-side only when a key is configured (else no-op).
  let posthog = null;
  onMount(() => {
    posthog = initPostHog(env.PUBLIC_POSTHOG_KEY, env.PUBLIC_POSTHOG_HOST);
    if (posthog) posthog.capture('$pageview');
    // Live-chat support (v0.0.18): load the Chatwoot widget when configured (else no-op).
    initChatwoot(env.PUBLIC_CHATWOOT_BASE_URL, env.PUBLIC_CHATWOOT_WEBSITE_TOKEN);
  });
  afterNavigate(() => {
    if (browser && posthog) posthog.capture('$pageview');
  });
</script>

<header>
  <a href="/" class="brand">🛒 Storefront</a>
  <form method="GET" action="/search">
    <input name="q" placeholder="Search products…" />
    <button type="submit">Search</button>
  </form>
  <nav>
    <a href="/cart">Cart</a>
    {#if data?.user}
      <span class="who">{data.user.username}</span>
      <form method="POST" action="/logout" class="inline">
        <button type="submit" class="link">Logout</button>
      </form>
    {:else}
      <a href="/login">Login</a>
    {/if}
  </nav>
</header>

<main>
  {@render children()}
</main>

<footer>Scalable E-Commerce Platform · SvelteKit SSR · talks to api-gateway over REST</footer>

<style>
  :global(body) { font-family: system-ui, sans-serif; margin: 0; color: #1a1a1a; }
  header { display: flex; gap: 1rem; align-items: center; padding: 1rem 1.5rem; background: #111; color: #fff; }
  .brand { color: #fff; text-decoration: none; font-weight: 700; font-size: 1.1rem; }
  form { display: flex; gap: .5rem; margin-left: auto; }
  input { padding: .45rem .6rem; border: 1px solid #ccc; border-radius: 6px; }
  button { padding: .45rem .9rem; border: 0; border-radius: 6px; background: #4f46e5; color: #fff; cursor: pointer; }
  nav { display: flex; gap: 1rem; align-items: center; }
  nav a { color: #fff; text-decoration: none; }
  nav .who { color: #bbb; font-size: .85rem; }
  nav form.inline { margin: 0; }
  nav .link { background: none; padding: 0; color: #fff; text-decoration: underline; }
  main { max-width: 880px; margin: 2rem auto; padding: 0 1.5rem; }
  footer { text-align: center; color: #777; font-size: .8rem; padding: 2rem 0; }
</style>
