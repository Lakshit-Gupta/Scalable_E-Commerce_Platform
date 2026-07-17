<script>
  import { enhance } from '$app/forms';
  let { data, form } = $props();
  let checking = $state(false);
</script>

<h1>Your cart</h1>
<p class="muted sub">{data.items.length} item{data.items.length === 1 ? '' : 's'}</p>

{#if form?.ordered}
  <div class="ok">
    <div class="ok-ico">✓</div>
    <div>
      <strong>Order placed!</strong>
      <p>
        <code>{form.orderId}</code> · {form.status} · total ₹{form.total}.
        <a href="/">Continue shopping →</a>
      </p>
    </div>
  </div>
{/if}
{#if form?.error}<p class="err">{form.error}</p>{/if}

{#if data.items.length}
  <div class="layout">
    <div class="lines">
      {#each data.items as item}
        <div class="line">
          <a class="thumb" href={`/product/${item.productId}`} aria-hidden="true"
            >{(item.name ?? '?').charAt(0).toUpperCase()}</a
          >
          <div class="meta">
            <a href={`/product/${item.productId}`} class="lname">{item.name}</a>
            <span class="muted">Qty {item.quantity} · {item.price != null ? `₹${item.price}` : '—'} each</span>
          </div>
          <div class="lt">{item.lineTotal != null ? `₹${item.lineTotal}` : '—'}</div>
          <form method="POST" action="?/remove" use:enhance class="rm">
            <input type="hidden" name="productId" value={item.productId} />
            <button type="submit" aria-label="Remove">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" /></svg>
            </button>
          </form>
        </div>
      {/each}
    </div>

    <aside class="summary">
      <h2>Summary</h2>
      <div class="row"><span>Subtotal</span><span class="amt">₹{data.total}</span></div>
      <div class="row"><span>Shipping</span><span class="free">Free</span></div>
      <div class="row total"><span>Total</span><span class="amt">₹{data.total}</span></div>
      <form
        method="POST"
        action="?/checkout"
        use:enhance={() => {
          checking = true;
          return async ({ update }) => {
            await update();
            checking = false;
          };
        }}
      >
        <button type="submit" class="btn checkout" disabled={checking}>
          {checking ? 'Placing order…' : 'Checkout'}
        </button>
      </form>
      <p class="note muted">Secure checkout · payment via gateway</p>
    </aside>
  </div>
{:else if !form?.ordered}
  <div class="empty">
    <div class="empty-ico">🛍</div>
    <p>Your cart is empty.</p>
    <a href="/" class="btn">Browse the catalog</a>
  </div>
{/if}

<style>
  .sub { margin-top: -0.25rem; }
  .ok {
    display: flex;
    gap: 0.85rem;
    align-items: center;
    background: var(--ok-soft);
    border: 1px solid color-mix(in srgb, var(--ok) 35%, transparent);
    border-radius: var(--radius);
    padding: 1rem 1.15rem;
    margin: 1rem 0 1.5rem;
  }
  .ok-ico {
    flex: none;
    width: 36px;
    height: 36px;
    display: grid;
    place-items: center;
    border-radius: 50%;
    background: var(--ok);
    color: #fff;
    font-weight: 700;
  }
  .ok strong { color: var(--ok); }
  .ok p { margin: 0.15rem 0 0; font-size: 0.9rem; color: var(--ink-soft); }
  .ok code { background: var(--surface-2); padding: 0.1rem 0.4rem; border-radius: 6px; font-size: 0.85em; }
  .err { color: var(--err); background: var(--err-soft); padding: 0.7rem 1rem; border-radius: var(--radius-sm); }

  .layout { display: grid; grid-template-columns: 1fr 320px; gap: 1.5rem; align-items: start; margin-top: 1.5rem; }
  .lines { display: flex; flex-direction: column; gap: 0.75rem; }
  .line {
    display: grid;
    grid-template-columns: 56px 1fr auto auto;
    gap: 1rem;
    align-items: center;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-radius: var(--radius);
    padding: 0.85rem 1rem;
    transition: border-color 0.14s ease;
  }
  .line:hover { border-color: color-mix(in srgb, var(--accent) 30%, var(--border)); }
  .thumb {
    width: 56px;
    height: 56px;
    border-radius: 16px 16px 16px 5px;
    display: grid;
    place-items: center;
    font-family: var(--disp);
    font-size: 1.5rem;
    font-weight: 800;
    color: #fdf7ec;
    text-decoration: none;
    background: var(--teal);
  }
  .meta { display: flex; flex-direction: column; gap: 0.15rem; overflow: hidden; }
  .lname { font-weight: 600; color: var(--ink); text-decoration: none; }
  .lname:hover { color: var(--accent); }
  .meta .muted { font-size: 0.82rem; }
  .lt { font-family: var(--mono); font-weight: 600; color: var(--ink); white-space: nowrap; }
  .rm button {
    width: 34px;
    height: 34px;
    display: grid;
    place-items: center;
    border: 1px solid var(--border);
    border-radius: 9px;
    background: var(--surface);
    color: var(--muted);
    cursor: pointer;
    transition: all 0.14s ease;
  }
  .rm button:hover { border-color: var(--err); color: var(--err); background: var(--err-soft); }
  .rm svg { width: 16px; height: 16px; stroke: currentColor; fill: none; stroke-width: 1.8; }

  /* the summary is a printed receipt: mono digits, dashed rules, torn bottom edge */
  .summary {
    position: sticky;
    top: 6rem;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-bottom: 0;
    border-radius: var(--radius-sm) var(--radius-sm) 0 0;
    padding: 1.25rem 1.25rem 1.5rem;
    box-shadow: var(--shadow);
  }
  .summary::after {
    content: '';
    position: absolute;
    left: -1.5px;
    right: -1.5px;
    bottom: -8px;
    height: 8px;
    background:
      linear-gradient(45deg, var(--surface) 4px, transparent 0) 0 0 / 12px 8px repeat-x,
      linear-gradient(-45deg, var(--surface) 4px, transparent 0) 0 0 / 12px 8px repeat-x;
  }
  .summary h2 { font-size: 1.05rem; margin-bottom: 1rem; }
  .row {
    display: flex; justify-content: space-between; padding: 0.5rem 0;
    color: var(--ink-soft); font-size: 0.92rem;
    border-bottom: 1.5px dashed var(--border);
  }
  .row .amt { font-family: var(--mono); }
  .row .free { color: var(--ok); font-weight: 700; }
  .row.total {
    border-bottom: 0;
    margin-top: 0.35rem;
    padding-top: 0.85rem;
    font-size: 1.15rem;
    font-weight: 700;
    color: var(--ink);
  }
  .checkout { width: 100%; padding: 0.8rem; margin-top: 1rem; font-size: 0.95rem; }
  .checkout:disabled { opacity: 0.65; cursor: default; }
  .note { text-align: center; font-size: 0.78rem; margin: 0.75rem 0 0; }

  .empty {
    text-align: center;
    padding: 4rem 1rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.85rem;
  }
  .empty-ico { font-size: 3rem; }
  .empty p { color: var(--muted); margin: 0; }

  @media (max-width: 760px) {
    .layout { grid-template-columns: 1fr; }
    .summary { position: static; }
  }
</style>
