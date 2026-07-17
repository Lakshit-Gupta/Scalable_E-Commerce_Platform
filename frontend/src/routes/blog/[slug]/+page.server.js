import { error } from '@sveltejs/kit';
import { getPost, lexicalToHtml, cmsMediaUrl } from '$lib/server/cms';

/** Blog post detail (SSR, v0.1.1): renders a published Payload post; missing/unpublished → 404. */
export async function load({ fetch, params }) {
  const post = await getPost(fetch, params.slug);
  if (!post) {
    throw error(404, 'Post not found');
  }
  return {
    post: {
      title: post.title,
      author: post.author ?? '',
      publishedAt: post.publishedAt ?? null,
      cover: cmsMediaUrl(post.coverImage),
      html: lexicalToHtml(post.content)
    }
  };
}
