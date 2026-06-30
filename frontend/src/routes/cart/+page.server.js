import { fail, redirect } from '@sveltejs/kit';
import { gatewayFetch, getJson } from '$lib/server/gateway';

/** Read the cart (HASH of product:{id} -> qty) and enrich each line with product name/price. */
async function readCart(fetch, token) {
  const res = await gatewayFetch(fetch, '/api/cart', token);
  const raw = res.ok ? await res.json() : {};
  const entries = Object.entries(raw).map(([key, qty]) => ({
    productId: key.replace(/^product:/, ''),
    quantity: Number(qty)
  }));
  const items = await Promise.all(
    entries.map(async (e) => {
      const p = await getJson(fetch, `/api/products/${e.productId}`, null);
      const price = p?.price != null ? Number(p.price) : null;
      return {
        productId: e.productId,
        quantity: e.quantity,
        name: p?.name ?? e.productId,
        price,
        lineTotal: price != null ? price * e.quantity : null
      };
    })
  );
  const total = items.reduce((sum, i) => sum + (i.lineTotal ?? 0), 0);
  return { items, total };
}

export async function load({ fetch, locals }) {
  if (!locals.user) {
    throw redirect(303, '/login?redirectTo=/cart');
  }
  return await readCart(fetch, locals.user.token);
}

export const actions = {
  add: async ({ request, fetch, locals }) => {
    if (!locals.user) return fail(401, { error: 'Please log in.' });
    const form = await request.formData();
    const productId = form.get('productId');
    const quantity = Number(form.get('quantity') ?? 1);
    await gatewayFetch(fetch, '/api/cart/items', locals.user.token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId, quantity })
    });
    return { added: true };
  },

  remove: async ({ request, fetch, locals }) => {
    if (!locals.user) return fail(401, { error: 'Please log in.' });
    const form = await request.formData();
    const productId = form.get('productId');
    await gatewayFetch(fetch, `/api/cart/items/${productId}`, locals.user.token, { method: 'DELETE' });
    return { removed: true };
  },

  checkout: async ({ fetch, locals }) => {
    if (!locals.user) return fail(401, { error: 'Please log in.' });
    const token = locals.user.token;

    const { items, total } = await readCart(fetch, token);
    if (!items.length) {
      return fail(400, { error: 'Your cart is empty.' });
    }

    const orderRes = await gatewayFetch(fetch, '/api/orders', token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        totalAmount: total,
        items: items.map((i) => ({ productId: i.productId, quantity: i.quantity, price: i.price ?? 0 }))
      })
    });
    if (!orderRes.ok) {
      return fail(502, { error: 'Could not place the order — please try again.' });
    }
    const order = await orderRes.json();

    // Order placed → empty the cart.
    await gatewayFetch(fetch, '/api/cart', token, { method: 'DELETE' });
    return { ordered: true, orderId: order.id, status: order.status, total };
  }
};
