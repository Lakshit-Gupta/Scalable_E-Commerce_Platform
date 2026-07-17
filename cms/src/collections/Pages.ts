import type { CollectionConfig } from 'payload'

// Marketing / content pages (home hero, about, landing pages). Consumed by the storefront BFF
// over the public REST API (GET /api/pages?where[slug][equals]=...).
export const Pages: CollectionConfig = {
  slug: 'pages',
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'slug', 'status', 'updatedAt'],
  },
  access: {
    // Only published pages are publicly readable; drafts stay admin-only.
    read: ({ req }) => {
      if (req.user) return true
      return { status: { equals: 'published' } }
    },
  },
  fields: [
    { name: 'title', type: 'text', required: true },
    {
      name: 'slug',
      type: 'text',
      required: true,
      unique: true,
      index: true,
      admin: { description: 'URL key, e.g. "home" or "about".' },
    },
    { name: 'subtitle', type: 'text' },
    {
      name: 'body',
      type: 'richText',
      admin: { description: 'Main page content (rich text).' },
    },
    {
      name: 'ctaLabel',
      type: 'text',
      admin: { description: 'Optional call-to-action button label.' },
    },
    { name: 'ctaHref', type: 'text' },
    {
      name: 'status',
      type: 'select',
      required: true,
      defaultValue: 'draft',
      options: [
        { label: 'Draft', value: 'draft' },
        { label: 'Published', value: 'published' },
      ],
    },
  ],
}
