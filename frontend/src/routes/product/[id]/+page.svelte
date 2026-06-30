<script>
  import { enhance } from '$app/forms';
  let { data } = $props();
  const p = data.product;
</script>

<a href="/" class="back">← Back to catalog</a>

<article class="detail">
  <h1>{p.name ?? p.id}</h1>
  {#if p.brand || p.category}
    <p class="muted">
      {p.brand ?? ''}{#if p.brand && p.category} · {/if}{p.category ?? ''}
    </p>
  {/if}
  {#if p.description}<p class="desc">{p.description}</p>{/if}
  {#if p.price != null}<div class="price big">₹{p.price}</div>{/if}
  {#if p.stockQuantity > 0}
    <p class="stock in">In stock ({p.stockQuantity})</p>
    <form method="POST" action="?/add" use:enhance class="addform">
      <input type="number" name="quantity" value="1" min="1" max={p.stockQuantity} />
      <button type="submit" class="add">Add to cart</button>
    </form>
  {:else}
    <p class="stock out">Out of stock</p>
  {/if}
</article>

<section>
  <h2>More like this</h2>
  {#if data.similar.length}
    <ul class="grid">
      {#each data.similar as s}
        <li>
          <a href={`/product/${s.id}`}>
            <strong>{s.name ?? s.id}</strong>
            {#if s.brand}<span class="muted"> · {s.brand}</span>{/if}
            {#if s.price != null}<div class="price">₹{s.price}</div>{/if}
          </a>
        </li>
      {/each}
    </ul>
  {:else}
    <p class="muted">No similar products yet.</p>
  {/if}
</section>

<style>
  .back { color: #4f46e5; text-decoration: none; font-size: .9rem; }
  .detail { margin: 1rem 0 2rem; }
  .desc { color: #333; }
  .muted { color: #777; }
  .price { margin-top: .5rem; font-weight: 700; color: #4f46e5; }
  .price.big { font-size: 1.5rem; margin: .75rem 0; }
  .stock.in { color: #157347; }
  .stock.out { color: #b02a37; }
  .addform { display: flex; gap: .5rem; align-items: center; margin: .75rem 0; }
  .addform input { width: 4rem; padding: .45rem .5rem; border: 1px solid #ccc; border-radius: 6px; }
  .add { padding: .5rem 1rem; border: 0; border-radius: 6px; background: #4f46e5; color: #fff; cursor: pointer; }
  .grid { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; }
  .grid li { border: 1px solid #eee; border-radius: 8px; padding: 1rem; }
  .grid a { text-decoration: none; color: inherit; display: block; }
</style>
