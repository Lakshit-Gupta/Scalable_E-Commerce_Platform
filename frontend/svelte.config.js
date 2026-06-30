import adapter from '@sveltejs/adapter-node';

/** SvelteKit storefront (v0.0.16). Node adapter -> standalone server, deployable behind Cloudflare. */
const config = {
  kit: {
    adapter: adapter()
  }
};

export default config;
