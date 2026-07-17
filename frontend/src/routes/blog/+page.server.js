import { getPosts, cmsMediaUrl } from '$lib/server/cms';

/** Blog index (SSR, v0.1.1): lists published posts from Payload CMS via the BFF. */
export async function load({ fetch }) {
  const posts = await getPosts(fetch, 30);
  return {
    posts: posts.map((p) => ({
      slug: p.slug,
      title: p.title,
      excerpt: p.excerpt ?? '',
      author: p.author ?? '',
      publishedAt: p.publishedAt ?? null,
      cover: cmsMediaUrl(p.coverImage)
    }))
  };
}
