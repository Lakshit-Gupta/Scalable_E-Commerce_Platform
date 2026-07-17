<script>
  let { product: p } = $props();
  const initial = $derived((p.name ?? '?').trim().charAt(0).toUpperCase());
  // fixed bazaar dye set — hash the id/name to a stable, flat panel colour (no gradients)
  const DYES = ['#be3455', '#175e52', '#d98e21', '#4a5fa5', '#5e7f3c', '#8e4585'];
  const dye = $derived(
    DYES[
      String(p.id ?? p.name ?? '')
        .split('')
        .reduce((a, c) => a + c.charCodeAt(0), 0) % DYES.length
    ]
  );
  const inStock = $derived(p.stockQuantity == null || p.stockQuantity > 0);
</script>

<a class="card" href={`/product/${p.id}`} style={`--dye:${dye}`}>
  <div class="thumb" aria-hidden="true">
    <span class="glyph">{initial}</span>
    <span class="glyph ghost">{initial}</span>
    {#if !inStock}<span class="oos">Sold out</span>{/if}
  </div>
  <div class="body">
    <div class="top">
      {#if p.category}<span class="badge">{p.category}</span>{/if}
      {#if p.averageRating}<span class="rating">★ {p.averageRating}</span>{/if}
    </div>
    <strong class="name">{p.name ?? p.id}</strong>
    {#if p.brand}<span class="brand">{p.brand}</span>{/if}
    <div class="foot">
      {#if p.price != null}<span class="price">₹{p.price}</span>{/if}
      <span class="view">View<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg></span>
    </div>
  </div>
</a>

<style>
  .card {
    display: flex;
    flex-direction: column;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    text-decoration: none;
    color: inherit;
    transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
  }
  .card:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-hover);
    border-color: color-mix(in srgb, var(--dye, var(--teal)) 55%, var(--border));
  }
  .thumb {
    position: relative;
    aspect-ratio: 4 / 3;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--dye);
    overflow: hidden;
  }
  .glyph {
    font-family: var(--disp);
    font-size: 3.4rem;
    font-weight: 800;
    color: #fdf7ec;
    z-index: 1;
    transition: transform 0.25s ease;
  }
  /* oversized outline echo behind the glyph — flat poster feel */
  .glyph.ghost {
    position: absolute;
    z-index: 0;
    font-size: 9rem;
    color: transparent;
    -webkit-text-stroke: 1.5px rgba(253, 247, 236, 0.22);
    transform: translate(28%, 8%) rotate(-8deg);
    transition: none;
    user-select: none;
  }
  .card:hover .glyph:not(.ghost) { transform: scale(1.08) rotate(-4deg); }
  .oos {
    position: absolute;
    top: 0.6rem;
    left: 0.6rem;
    z-index: 2;
    background: rgba(20, 25, 20, 0.72);
    color: #fdf7ec;
    font-size: 0.66rem;
    font-weight: 700;
    padding: 0.2rem 0.55rem;
    border-radius: 999px;
  }
  .body { padding: 0.9rem 1rem 1.05rem; display: flex; flex-direction: column; gap: 0.3rem; flex: 1; }
  .top { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; min-height: 1.2rem; }
  .badge {
    font-size: 0.64rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--teal);
    background: var(--teal-soft);
    padding: 0.18rem 0.55rem;
    border-radius: 999px;
    font-weight: 700;
  }
  .rating { font-size: 0.78rem; color: var(--gold); font-weight: 700; white-space: nowrap; }
  .name { font-size: 0.96rem; line-height: 1.3; color: var(--ink); font-weight: 600; }
  .brand { color: var(--muted); font-size: 0.8rem; }
  .foot { margin-top: auto; padding-top: 0.6rem; display: flex; align-items: center; justify-content: space-between; }
  .price { font-family: var(--mono); font-weight: 600; font-size: 1rem; color: var(--ink); }
  .view {
    display: inline-flex; align-items: center; gap: 0.2rem;
    font-size: 0.8rem; font-weight: 700; color: var(--accent);
    opacity: 0; transform: translateX(-4px);
    transition: opacity 0.16s ease, transform 0.16s ease;
  }
  .view svg { width: 14px; height: 14px; stroke: currentColor; fill: none; stroke-width: 2; }
  .card:hover .view { opacity: 1; transform: translateX(0); }
</style>
