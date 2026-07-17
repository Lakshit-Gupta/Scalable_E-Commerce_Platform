<script>
  let { data } = $props();
  const fmt = (d) => (d ? new Date(d).toLocaleDateString() : '');
</script>

<svelte:head><title>Blog — ShopSphere</title></svelte:head>

<section class="wrap">
  <h1>Blog</h1>

  {#if data.posts.length === 0}
    <p class="empty">No posts published yet.</p>
  {:else}
    <div class="grid">
      {#each data.posts as post}
        <a class="card" href={`/blog/${post.slug}`}>
          {#if post.cover}
            <img src={post.cover} alt={post.title} loading="lazy" />
          {/if}
          <div class="body">
            <h2>{post.title}</h2>
            {#if post.excerpt}<p class="excerpt">{post.excerpt}</p>{/if}
            <div class="meta">
              {#if post.author}<span>{post.author}</span>{/if}
              {#if post.publishedAt}<span>{fmt(post.publishedAt)}</span>{/if}
            </div>
          </div>
        </a>
      {/each}
    </div>
  {/if}
</section>

<style>
  .wrap { max-width: 960px; margin: 0 auto; padding: 1.5rem 1rem; }
  h1 { margin: 0 0 1rem; }
  .empty { color: var(--muted, #666); }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1rem; }
  .card {
    display: flex; flex-direction: column;
    background: var(--surface); border: 1.5px solid var(--border, #e5e5e5);
    border-radius: var(--radius, 12px); overflow: hidden;
    text-decoration: none; color: inherit;
    transition: box-shadow 0.15s ease, transform 0.15s ease, border-color 0.15s ease;
  }
  .card:hover { box-shadow: var(--shadow-hover); transform: translateY(-3px); border-color: var(--gold); }
  .card img { width: 100%; aspect-ratio: 16/9; object-fit: cover; }
  .body { padding: .85rem 1rem 1rem; }
  .body h2 { font-size: 1.05rem; margin: 0 0 .35rem; }
  .excerpt { margin: 0 0 .6rem; color: var(--muted, #555); font-size: .9rem; }
  .meta { display: flex; gap: .75rem; font-size: .78rem; color: var(--muted, #888); }
</style>
