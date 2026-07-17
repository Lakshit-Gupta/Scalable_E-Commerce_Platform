<script>
  import ProductCard from '$lib/ProductCard.svelte';
  let { data } = $props();
</script>

<div class="head">
  <h1>Search</h1>
  {#if data.q}
    <p class="muted">
      <strong>{data.results.length}</strong> result{data.results.length === 1 ? '' : 's'} for
      “{data.q}”
    </p>
  {:else}
    <p class="muted">Type a query in the bar above to search the catalog.</p>
  {/if}
</div>

{#if data.results.length}
  <div class="grid">
    {#each data.results as p}
      <ProductCard product={p} />
    {/each}
  </div>
{:else if data.q}
  <div class="empty">
    <div class="empty-ico">🔍</div>
    <p>No matches for “{data.q}”.</p>
    <p class="muted">Try a broader term or check the spelling.</p>
    <a href="/" class="btn ghost">Back to catalog</a>
  </div>
{/if}

<style>
  .head { margin-bottom: 1.75rem; }
  .head .muted { margin-top: -0.25rem; }
  .empty {
    text-align: center;
    padding: 3.5rem 1rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.4rem;
  }
  .empty-ico { font-size: 2.75rem; margin-bottom: 0.5rem; }
  .empty p { margin: 0; }
  .empty .btn { margin-top: 1rem; }
</style>
