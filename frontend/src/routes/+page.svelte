<script>
  let { data } = $props();
</script>

<h1>Welcome</h1>

<section>
  <h2>Trending now</h2>
  {#if data.trending.length}
    <ol>
      {#each data.trending as item}
        <li><a href={`/product/${item.productId}`}>{item.productId}</a> — {item.count} purchases</li>
      {/each}
    </ol>
  {:else}
    <p class="muted">No trending data yet — place some orders.</p>
  {/if}
</section>

<section>
  <h2>Catalog</h2>
  {#if data.products.length}
    <ul class="grid">
      {#each data.products as p}
        <li>
          <a href={`/product/${p.id}`}>
            <strong>{p.name ?? p.id}</strong>
            {#if p.brand}<span class="muted"> · {p.brand}</span>{/if}
            {#if p.price != null}<div class="price">₹{p.price}</div>{/if}
          </a>
        </li>
      {/each}
    </ul>
  {:else}
    <p class="muted">No products found.</p>
  {/if}
</section>

<style>
  section { margin-bottom: 2rem; }
  .muted { color: #777; }
  .grid { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; }
  .grid li { border: 1px solid #eee; border-radius: 8px; padding: 1rem; }
  .grid a { text-decoration: none; color: inherit; display: block; }
  .price { margin-top: .5rem; font-weight: 700; color: #4f46e5; }
</style>
