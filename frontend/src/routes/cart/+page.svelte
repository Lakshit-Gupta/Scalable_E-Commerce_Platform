<script>
  import { enhance } from '$app/forms';
  let { data, form } = $props();
</script>

<h1>Your cart</h1>

{#if form?.ordered}
  <div class="ok">
    Order placed — <strong>{form.orderId}</strong> ({form.status}), total ₹{form.total}.
    <a href="/">Continue shopping</a>
  </div>
{/if}
{#if form?.error}<p class="err">{form.error}</p>{/if}

{#if data.items.length}
  <table>
    <thead>
      <tr><th>Product</th><th>Qty</th><th>Price</th><th>Line</th><th></th></tr>
    </thead>
    <tbody>
      {#each data.items as item}
        <tr>
          <td><a href={`/product/${item.productId}`}>{item.name}</a></td>
          <td>{item.quantity}</td>
          <td>{item.price != null ? `₹${item.price}` : '—'}</td>
          <td>{item.lineTotal != null ? `₹${item.lineTotal}` : '—'}</td>
          <td>
            <form method="POST" action="?/remove" use:enhance>
              <input type="hidden" name="productId" value={item.productId} />
              <button type="submit" class="link">Remove</button>
            </form>
          </td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="total">Total: <strong>₹{data.total}</strong></div>

  <form method="POST" action="?/checkout" use:enhance>
    <button type="submit" class="checkout">Checkout</button>
  </form>
{:else}
  <p class="muted">Your cart is empty. <a href="/">Browse the catalog</a>.</p>
{/if}

<style>
  .muted { color: #777; }
  .err { color: #b02a37; }
  .ok { background: #e8f5ee; border: 1px solid #157347; color: #0f5132; padding: .75rem 1rem; border-radius: 8px; margin-bottom: 1rem; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 1rem; }
  th, td { text-align: left; padding: .5rem .4rem; border-bottom: 1px solid #eee; }
  .total { text-align: right; margin: 1rem 0; font-size: 1.1rem; }
  .checkout { padding: .6rem 1.2rem; border: 0; border-radius: 6px; background: #4f46e5; color: #fff; cursor: pointer; }
  .link { background: none; border: 0; color: #b02a37; cursor: pointer; padding: 0; text-decoration: underline; }
  a { color: #4f46e5; }
</style>
