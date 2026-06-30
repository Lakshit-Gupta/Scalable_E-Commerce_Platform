import { error, redirect } from '@sveltejs/kit';
import { getJson, gatewayFetch } from '$lib/server/gateway';

/**
 * Product detail page (SSR, v0.0.20): fetches the product plus its content-based "more like this"
 * recommendations (Elasticsearch MLT, built v0.0.19) via the gateway. A missing product → 404.
 */
export async function load({ fetch, params }) {
  const { id } = params;
  const product = await getJson(fetch, `/api/products/${id}`, null);
  if (!product) {
    throw error(404, 'Product not found');
  }
  const similar = await getJson(fetch, `/api/products/${id}/similar?size=6`, []);
  return { product, similar };
}

export const actions = {
  /** Add this product to the cart, then go to the cart (auth-gated → login first, v0.1.0). */
  add: async ({ fetch, locals, params, request }) => {
    if (!locals.user) {
      throw redirect(303, `/login?redirectTo=/product/${params.id}`);
    }
    const form = await request.formData();
    const quantity = Number(form.get('quantity') ?? 1);
    await gatewayFetch(fetch, '/api/cart/items', locals.user.token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId: params.id, quantity })
    });
    throw redirect(303, '/cart');
  }
};
