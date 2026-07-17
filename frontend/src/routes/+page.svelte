<script>
  import ProductCard from '$lib/ProductCard.svelte';
  let { data } = $props();
  const categories = $derived([...new Set(data.products.map((p) => p.category).filter(Boolean))].sort());

  // Resolve trending productIds to real products from the catalog for nicer display.
  const trending = $derived.by(() => {
    const byId = new Map(data.products.map((p) => [p.id, p]));
    return data.trending
      .map((t) => ({ ...t, product: byId.get(t.productId) }))
      .filter((t) => t.product)
      .slice(0, 4);
  });
</script>

<section class="hero">
  <div class="hero-copy">
    <span class="eyebrow">Open daily · free shipping over ₹999</span>
    <h1>The whole bazaar,<br /><em>one doorstep.</em></h1>
    <p>
      {data.products.length}+ products across electronics, home, books & fashion — with
      Elasticsearch search and live recommendations.
    </p>
    <form method="GET" action="/search" class="hero-search" role="search">
      <input name="q" placeholder="Try “headphones”, “running shoes”, “coffee”…" aria-label="Search" />
      <button type="submit" class="btn">Search</button>
    </form>
    {#if categories.length}
      <div class="chips">
        {#each categories.slice(0, 8) as c}
          <a class="chip" href={`/search?q=${encodeURIComponent(c)}`}>{c}</a>
        {/each}
      </div>
    {/if}
  </div>
  <div class="hero-art" aria-hidden="true">
    <span class="tag t1">₹</span>
    <span class="tag t2">★</span>
    <span class="tag t3">✦</span>
  </div>
</section>

{#if trending.length}
  <section>
    <h2>Moving fast in the market</h2>
    <div class="trending">
      {#each trending as item, i}
        <a class="trend" href={`/product/${item.productId}`}>
          <span class="rank">{i + 1}</span>
          <span class="t-info">
            <strong>{item.product.name}</strong>
            <span class="muted">{item.count} purchases</span>
          </span>
        </a>
      {/each}
    </div>
  </section>
{/if}

<section>
  <h2>Catalog</h2>
  {#if data.products.length}
    <div class="grid">
      {#each data.products as p}
        <ProductCard product={p} />
      {/each}
    </div>
  {:else}
    <p class="muted">No products yet — seed the catalog to fill the stalls.</p>
  {/if}
</section>

<style>
  .hero {
    position: relative;
    display: grid;
    grid-template-columns: 1.4fr 1fr;
    gap: 1rem;
    align-items: center;
    overflow: hidden;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-radius: 28px;
    padding: 2.75rem 2.5rem;
    margin-bottom: 2.5rem;
    box-shadow: var(--shadow);
  }
  .hero-copy { position: relative; z-index: 1; }
  .hero-copy > * { animation: rise 0.5s ease both; }
  .hero-copy > *:nth-child(2) { animation-delay: 0.06s; }
  .hero-copy > *:nth-child(3) { animation-delay: 0.12s; }
  .hero-copy > *:nth-child(4) { animation-delay: 0.18s; }
  .hero-copy > *:nth-child(5) { animation-delay: 0.24s; }
  @keyframes rise {
    from { opacity: 0; transform: translateY(12px); }
    to { opacity: 1; transform: translateY(0); }
  }
  .eyebrow {
    display: inline-block;
    font-size: 0.74rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    color: var(--teal);
    background: var(--teal-soft);
    padding: 0.3rem 0.7rem;
    border-radius: 999px;
    margin-bottom: 1rem;
  }
  .hero h1 { font-size: clamp(2.1rem, 4.5vw, 3.1rem); font-weight: 800; margin-bottom: 0.75rem; }
  .hero h1 em { font-style: normal; color: var(--accent); }
  .hero p { margin: 0 0 1.5rem; max-width: 460px; color: var(--ink-soft); line-height: 1.6; }
  .hero-search { display: flex; gap: 0.5rem; max-width: 440px; margin-bottom: 1.25rem; }
  .hero-search input {
    flex: 1;
    padding: 0.75rem 1rem;
    border: 1.5px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--bg);
    color: var(--ink);
    font: inherit;
    font-size: 0.95rem;
    outline: none;
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
  }
  .hero-search input:focus { border-color: var(--teal); box-shadow: 0 0 0 4px var(--teal-soft); }

  .chips { display: flex; flex-wrap: wrap; gap: 0.5rem; }
  .chip {
    text-decoration: none;
    color: var(--ink-soft);
    background: var(--surface-2);
    border: 1.5px solid var(--border);
    padding: 0.4rem 0.85rem;
    border-radius: 999px;
    font-size: 0.82rem;
    font-weight: 600;
    transition: all 0.14s ease;
  }
  .chip:hover { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }

  /* hanging price tags — flat dye fields, no gradients */
  .hero-art { position: relative; height: 240px; }
  .tag {
    position: absolute;
    display: grid;
    place-items: center;
    font-family: var(--disp);
    font-weight: 800;
    color: #fdf7ec;
    border-radius: 26px 26px 26px 8px;
    box-shadow: var(--shadow);
    animation: sway 7s ease-in-out infinite;
    transform-origin: top center;
  }
  .tag::before {
    content: '';
    position: absolute;
    top: 12px;
    left: 16px;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--surface);   /* punched tag hole */
  }
  .t1 { width: 150px; height: 170px; font-size: 3.6rem; right: 8%; top: 2%; background: var(--accent); transform: rotate(8deg); }
  .t2 { width: 115px; height: 130px; font-size: 2.7rem; right: 46%; top: 34%; background: var(--teal); transform: rotate(-10deg); animation-delay: -2.4s; }
  .t3 { width: 95px; height: 108px; font-size: 2.2rem; right: 6%; bottom: 2%; background: var(--gold); transform: rotate(-4deg); animation-delay: -4.6s; }
  @keyframes sway {
    0%, 100% { rotate: 0deg; }
    50% { rotate: 5deg; }
  }

  .trending { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 0.85rem; }
  .trend {
    display: flex;
    align-items: center;
    gap: 0.85rem;
    padding: 0.9rem 1rem;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-radius: var(--radius-sm);
    text-decoration: none;
    color: var(--ink);
    transition: border-color 0.14s ease, transform 0.14s ease;
  }
  .trend:hover { border-color: var(--gold); transform: translateY(-2px); }
  .rank {
    flex: none;
    width: 34px;
    height: 34px;
    display: grid;
    place-items: center;
    border-radius: 50% 50% 50% 8px;
    background: var(--gold-soft);
    color: var(--warn);
    font-family: var(--disp);
    font-weight: 800;
    font-size: 1.05rem;
  }
  .t-info { display: flex; flex-direction: column; gap: 0.1rem; overflow: hidden; }
  .t-info strong { font-size: 0.92rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .t-info .muted { font-size: 0.78rem; font-family: var(--mono); }

  @media (max-width: 760px) {
    .hero { grid-template-columns: 1fr; padding: 2rem 1.5rem; }
    .hero-art { display: none; }
  }
</style>
