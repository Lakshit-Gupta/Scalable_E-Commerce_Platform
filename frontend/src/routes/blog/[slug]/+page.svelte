<script>
  let { data } = $props();
  const { post } = data;
  const fmt = (d) => (d ? new Date(d).toLocaleDateString() : '');
</script>

<svelte:head><title>{post.title} — ShopSphere</title></svelte:head>

<article class="wrap">
  <a class="back" href="/blog">← Blog</a>
  <h1>{post.title}</h1>
  <div class="meta">
    {#if post.author}<span>{post.author}</span>{/if}
    {#if post.publishedAt}<span>{fmt(post.publishedAt)}</span>{/if}
  </div>
  {#if post.cover}
    <img class="cover" src={post.cover} alt={post.title} />
  {/if}
  <!-- CMS content is authored in the trusted Payload admin and serialized server-side. -->
  <div class="content">{@html post.html}</div>
</article>

<style>
  .wrap { max-width: 720px; margin: 0 auto; padding: 1.5rem 1rem 3rem; }
  .back { display: inline-block; margin-bottom: 1rem; color: var(--muted, #666); text-decoration: none; }
  h1 { margin: 0 0 .5rem; line-height: 1.2; }
  .meta { display: flex; gap: .75rem; font-size: .82rem; color: var(--muted, #888); margin-bottom: 1.25rem; }
  .cover { width: 100%; border-radius: var(--radius, 12px); margin-bottom: 1.5rem; }
  .content :global(p) { line-height: 1.7; margin: 0 0 1rem; }
  .content :global(h2), .content :global(h3) { margin: 1.6rem 0 .6rem; }
  .content :global(ul), .content :global(ol) { padding-left: 1.4rem; margin: 0 0 1rem; }
  .content :global(blockquote) { border-left: 3px solid var(--gold, #ddd); margin: 0 0 1rem; padding-left: 1rem; color: var(--muted, #555); }
  .content :global(a) { color: var(--teal, #3b5bdb); }
</style>
