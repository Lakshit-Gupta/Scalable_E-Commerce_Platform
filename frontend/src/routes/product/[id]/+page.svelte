<script>
  import { enhance } from '$app/forms';
  import ProductCard from '$lib/ProductCard.svelte';
  let { data, form } = $props();
  const p = data.product;
  const initial = (p.name ?? '?').trim().charAt(0).toUpperCase();
  // same fixed dye set as ProductCard so the detail page matches the card colour
  const DYES = ['#be3455', '#175e52', '#d98e21', '#4a5fa5', '#5e7f3c', '#8e4585'];
  const dye =
    DYES[
      String(p.id ?? p.name ?? '')
        .split('')
        .reduce((a, c) => a + c.charCodeAt(0), 0) % DYES.length
    ];
  let adding = $state(false);
</script>

<a href="/" class="back">
  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5M11 18l-6-6 6-6" /></svg>
  Back to catalog
</a>

<article class="detail">
  <div class="hero-img" style={`--dye:${dye}`} aria-hidden="true">
    <span class="glyph">{initial}</span>
    <span class="glyph ghost">{initial}</span>
  </div>

  <div class="info">
    {#if p.category}<span class="badge">{p.category}</span>{/if}
    <h1>{p.name ?? p.id}</h1>
    {#if p.brand}<p class="brand">by {p.brand}</p>{/if}
    {#if p.averageRating}
      <p class="rating">
        <span class="stars" style={`--r:${p.averageRating}`} aria-hidden="true">★★★★★</span>
        {p.averageRating} <span class="muted">average rating</span>
      </p>
    {/if}
    {#if p.description}<p class="desc">{p.description}</p>{/if}

    <!-- Editorial enrichment from Payload CMS (v0.1.1), keyed by product id. -->
    {#if data.enrichment}
      {#if data.enrichment.badges.length}
        <div class="cms-badges">
          {#each data.enrichment.badges as b}<span class="cms-badge">{b}</span>{/each}
        </div>
      {/if}
      {#if data.enrichment.highlights.length}
        <ul class="highlights">
          {#each data.enrichment.highlights as h}<li>{h}</li>{/each}
        </ul>
      {/if}
    {/if}

    <div class="buy">
      {#if p.price != null}<div class="price">₹{p.price}</div>{/if}

      {#if p.stockQuantity > 0}
        <p class="stock in"><span class="pip"></span>In stock · {p.stockQuantity} available</p>
        <form
          method="POST"
          action="?/add"
          class="addform"
          use:enhance={() => {
            adding = true;
            return async ({ update }) => {
              await update();
              adding = false;
            };
          }}
        >
          <div class="qty">
            <input type="number" name="quantity" value="1" min="1" max={p.stockQuantity} aria-label="Quantity" />
          </div>
          <button type="submit" class="btn add" disabled={adding}>
            {#if adding}Adding…{:else}
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" /><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6" /></svg>
              Add to cart
            {/if}
          </button>
        </form>
      {:else}
        <p class="stock out"><span class="pip"></span>Out of stock</p>
      {/if}

      {#if form?.added}
        <p class="added">✓ Added to cart · <a href="/cart">view cart</a></p>
      {/if}
      {#if form?.error}<p class="err">{form.error}</p>{/if}
    </div>
  </div>
</article>

{#if data.enrichment?.storyHtml}
  <section class="story">
    <h2>The story</h2>
    <!-- CMS content authored in the trusted Payload admin, serialized server-side. -->
    <div class="story-body">{@html data.enrichment.storyHtml}</div>
  </section>
{/if}

<section>
  <h2>You might also like</h2>
  {#if data.similar.length}
    <div class="grid">
      {#each data.similar as s}
        <ProductCard product={s} />
      {/each}
    </div>
  {:else}
    <p class="muted">No similar products yet.</p>
  {/if}
</section>

<style>
  .back {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    color: var(--muted);
    text-decoration: none;
    font-size: 0.88rem;
    font-weight: 500;
  }
  .back:hover { color: var(--accent); }
  .back svg { width: 16px; height: 16px; stroke: currentColor; fill: none; stroke-width: 2; }

  .detail {
    display: grid;
    grid-template-columns: 380px 1fr;
    gap: 2.5rem;
    align-items: start;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-radius: 28px;
    padding: 1.75rem;
    margin: 1rem 0 3rem;
    box-shadow: var(--shadow);
  }
  .hero-img {
    position: relative;
    aspect-ratio: 1;
    border-radius: 26px 26px 26px 8px;   /* price-tag corner, matches the brand mark */
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    background: var(--dye);
  }
  .glyph { font-family: var(--disp); font-size: 6.5rem; font-weight: 800; color: #fdf7ec; z-index: 1; }
  .glyph.ghost {
    position: absolute;
    z-index: 0;
    font-size: 20rem;
    color: transparent;
    -webkit-text-stroke: 2px rgba(253, 247, 236, 0.2);
    transform: translate(24%, 10%) rotate(-8deg);
    user-select: none;
  }

  .info { display: flex; flex-direction: column; gap: 0.5rem; }
  .badge {
    align-self: flex-start;
    font-size: 0.68rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--teal);
    background: var(--teal-soft);
    padding: 0.2rem 0.6rem;
    border-radius: 999px;
    font-weight: 700;
  }
  .info h1 { margin: 0.4rem 0 0; }
  .brand { color: var(--muted); margin: 0; }
  .rating { display: flex; align-items: center; gap: 0.4rem; font-weight: 600; margin: 0.4rem 0; color: var(--ink-soft); }
  .stars {
    position: relative;
    font-size: 1rem;
    letter-spacing: 0.1em;
    color: var(--border);
  }
  .stars::before {
    content: '★★★★★';
    position: absolute;
    left: 0;
    top: 0;
    width: calc(var(--r) / 5 * 100%);
    overflow: hidden;
    color: var(--gold);
    white-space: nowrap;
  }
  .desc { color: var(--ink-soft); line-height: 1.7; margin: 0.5rem 0; }

  .buy {
    margin-top: 0.75rem;
    padding-top: 1.25rem;
    border-top: 1.5px dashed var(--border);
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }
  .price { font-family: var(--mono); font-size: 2.1rem; font-weight: 600; color: var(--ink); }
  .stock { display: flex; align-items: center; gap: 0.45rem; font-weight: 600; font-size: 0.9rem; margin: 0; }
  .stock .pip { width: 8px; height: 8px; border-radius: 50%; }
  .stock.in { color: var(--ok); }
  .stock.in .pip { background: var(--ok); box-shadow: 0 0 0 3px var(--ok-soft); }
  .stock.out { color: var(--err); }
  .stock.out .pip { background: var(--err); }

  .addform { display: flex; gap: 0.6rem; align-items: center; margin-top: 0.25rem; }
  .qty input {
    width: 4.5rem;
    padding: 0.65rem 0.7rem;
    border: 1.5px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font-family: var(--mono);
    font-size: 0.95rem;
    text-align: center;
    outline: none;
  }
  .qty input:focus { border-color: var(--teal); box-shadow: 0 0 0 4px var(--teal-soft); }
  .add { padding: 0.7rem 1.5rem; }
  .add svg { width: 17px; height: 17px; stroke: currentColor; fill: none; stroke-width: 1.8; }
  .add:disabled { opacity: 0.6; cursor: default; }
  .added { color: var(--ok); font-weight: 600; font-size: 0.9rem; margin: 0; }
  .added a { color: var(--ok); }
  .err { color: var(--err); font-weight: 500; font-size: 0.9rem; margin: 0; }

  .cms-badges { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0.25rem 0; }
  .cms-badge {
    font-size: 0.68rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    color: var(--ok);
    background: var(--ok-soft);
    padding: 0.2rem 0.6rem;
    border-radius: 999px;
  }
  .highlights { margin: 0.5rem 0; padding-left: 1.1rem; color: var(--ink-soft); line-height: 1.7; }
  .highlights li { margin: 0.1rem 0; }

  .story { margin: 0 0 3rem; }
  .story h2 { margin: 0 0 0.75rem; }
  .story-body { color: var(--ink-soft); line-height: 1.75; max-width: 70ch; }
  .story-body :global(p) { margin: 0 0 1rem; }
  .story-body :global(h2), .story-body :global(h3) { color: var(--ink); margin: 1.4rem 0 0.5rem; }
  .story-body :global(ul), .story-body :global(ol) { padding-left: 1.4rem; }

  @media (max-width: 720px) {
    .detail { grid-template-columns: 1fr; gap: 1.5rem; padding: 1.25rem; }
    .hero-img { aspect-ratio: 16 / 10; }
    .glyph { font-size: 4rem; }
  }
</style>
