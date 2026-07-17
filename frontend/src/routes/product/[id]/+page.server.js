import { error, redirect } from '@sveltejs/kit';
import { getJson, gatewayFetch } from '$lib/server/gateway';
import { getEnrichment, lexicalToHtml } from '$lib/server/cms';

/**
 * Product detail page (SSR, v0.0.20): fetches the product plus its content-based "more like this"
 * recommendations (Elasticsearch MLT, built v0.0.19) via the gateway. A missing product → 404.
 * v0.1.1: also merges editorial enrichment (highlights/story/badges) from Payload CMS, keyed by
 * product id — best-effort, so the page still renders if the CMS has nothing for this product.
 */
export async function load({ fetch, params }) {
  const { id } = params;
  const product = await getJson(fetch, `/api/products/${id}`, null);
  if (!product) {
    throw error(404, 'Product not found');
  }
  const [similar, enrichmentDoc] = await Promise.all([
    getJson(fetch, `/api/products/${id}/similar?size=6`, []),
    getEnrichment(fetch, id)
  ]);
  const enrichment = enrichmentDoc
    ? {
        highlights: (enrichmentDoc.highlights ?? []).map((h) => h.text).filter(Boolean),
        badges: (enrichmentDoc.badges ?? []).map((b) => b.label).filter(Boolean),
        storyHtml: lexicalToHtml(enrichmentDoc.story)
      }
    : null;
  return { product, similar, enrichment };
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
