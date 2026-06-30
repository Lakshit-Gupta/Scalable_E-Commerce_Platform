<script>
  let { data } = $props();
</script>

<h1>Search</h1>

{#if data.q}
  <p class="muted">Results for “{data.q}” — {data.results.length} found</p>
{:else}
  <p class="muted">Type a query above to search the catalog.</p>
{/if}

{#if data.results.length}
  <ul class="grid">
    {#each data.results as p}
      <li>
        <a href={`/product/${p.id}`}>
          <strong>{p.name ?? p.id}</strong>
          {#if p.brand}<span class="muted"> · {p.brand}</span>{/if}
          {#if p.price != null}<div class="price">₹{p.price}</div>{/if}
        </a>
      </li>
    {/each}
  </ul>
{/if}

<style>
  .muted { color: #777; }
  .grid { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; }
  .grid li { border: 1px solid #eee; border-radius: 8px; padding: 1rem; }
  .grid a { text-decoration: none; color: inherit; display: block; }
  .price { margin-top: .5rem; font-weight: 700; color: #4f46e5; }
</style>
