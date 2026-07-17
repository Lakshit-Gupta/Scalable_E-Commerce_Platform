import { env } from '$env/dynamic/private';

/**
 * CMS BFF helper (v0.1.1): SvelteKit server load functions read content from Payload CMS
 * server-to-server. The browser never talks to the CMS directly — only this Node server does.
 * Payload exposes a public REST API: GET /api/{collection}?where[field][op]=value.
 */
export const CMS_URL = env.CMS_URL || 'http://cms:3000';

/** GET a CMS JSON endpoint, returning a fallback on any error so a page never hard-fails. */
async function cmsJson(fetchFn, path, fallback) {
  try {
    const res = await fetchFn(`${CMS_URL}${path}`);
    if (!res.ok) return fallback;
    return await res.json();
  } catch {
    return fallback;
  }
}

/** Published blog posts, newest first. */
export async function getPosts(fetchFn, limit = 20) {
  const data = await cmsJson(
    fetchFn,
    `/api/posts?where[status][equals]=published&sort=-publishedAt&limit=${limit}&depth=1`,
    { docs: [] }
  );
  return data.docs ?? [];
}

/** A single published post by slug, or null. */
export async function getPost(fetchFn, slug) {
  const data = await cmsJson(
    fetchFn,
    `/api/posts?where[slug][equals]=${encodeURIComponent(slug)}&where[status][equals]=published&limit=1&depth=1`,
    { docs: [] }
  );
  return data.docs?.[0] ?? null;
}

/** A published marketing page by slug, or null. */
export async function getPage(fetchFn, slug) {
  const data = await cmsJson(
    fetchFn,
    `/api/pages?where[slug][equals]=${encodeURIComponent(slug)}&where[status][equals]=published&limit=1`,
    { docs: [] }
  );
  return data.docs?.[0] ?? null;
}

/** Published editorial enrichment for a product id, or null (product page merges it over catalog data). */
export async function getEnrichment(fetchFn, productId) {
  const data = await cmsJson(
    fetchFn,
    `/api/product-enrichment?where[productId][equals]=${encodeURIComponent(productId)}&where[status][equals]=published&limit=1`,
    { docs: [] }
  );
  return data.docs?.[0] ?? null;
}

/** Absolute URL for a CMS media upload (relative `url` from Payload → public CMS origin). */
export function cmsMediaUrl(media) {
  if (!media?.url) return null;
  if (/^https?:\/\//.test(media.url)) return media.url;
  const base = env.CMS_PUBLIC_URL || 'http://localhost:3002';
  return `${base}${media.url}`;
}

const esc = (s) =>
  String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

// Lexical text-format bitmask (Payload richtext): 1 bold, 2 italic, 8 underline, 4 strikethrough, 16 code.
function formatText(node) {
  let t = esc(node.text);
  const f = node.format || 0;
  if (f & 16) t = `<code>${t}</code>`;
  if (f & 1) t = `<strong>${t}</strong>`;
  if (f & 2) t = `<em>${t}</em>`;
  if (f & 4) t = `<s>${t}</s>`;
  if (f & 8) t = `<u>${t}</u>`;
  return t;
}

function renderChildren(children) {
  return (children ?? []).map(renderNode).join('');
}

function renderNode(node) {
  if (!node) return '';
  switch (node.type) {
    case 'text':
      return formatText(node);
    case 'linebreak':
      return '<br/>';
    case 'paragraph':
      return `<p>${renderChildren(node.children)}</p>`;
    case 'heading': {
      const tag = /^h[1-6]$/.test(node.tag) ? node.tag : 'h2';
      return `<${tag}>${renderChildren(node.children)}</${tag}>`;
    }
    case 'quote':
      return `<blockquote>${renderChildren(node.children)}</blockquote>`;
    case 'list': {
      const tag = node.listType === 'number' ? 'ol' : 'ul';
      return `<${tag}>${renderChildren(node.children)}</${tag}>`;
    }
    case 'listitem':
      return `<li>${renderChildren(node.children)}</li>`;
    case 'link': {
      const url = esc(node.fields?.url || '#');
      const rel = node.fields?.newTab ? ' target="_blank" rel="noopener"' : '';
      return `<a href="${url}"${rel}>${renderChildren(node.children)}</a>`;
    }
    default:
      // Unknown node: fall back to rendering its children so nothing is silently dropped.
      return renderChildren(node.children);
  }
}

/**
 * Serialize a Payload Lexical richtext value (`{ root: { children } }`) to a safe HTML string.
 * Covers the common editorial nodes; unknown nodes degrade to their children.
 */
export function lexicalToHtml(value) {
  const children = value?.root?.children;
  if (!children) return '';
  return renderChildren(children);
}
