import type { CollectionConfig } from 'payload'

// Product enrichment: editorial content keyed by the product-service product id. The storefront
// product page merges this over the catalog data (highlights, long-form copy, marketing badges).
export const ProductEnrichment: CollectionConfig = {
  slug: 'product-enrichment',
  admin: {
    useAsTitle: 'productId',
    defaultColumns: ['productId', 'status', 'updatedAt'],
  },
  access: {
    read: ({ req }) => {
      if (req.user) return true
      return { status: { equals: 'published' } }
    },
  },
  fields: [
    {
      name: 'productId',
      type: 'text',
      required: true,
      unique: true,
      index: true,
      admin: { description: 'Matches the product-service product id (UUID).' },
    },
    {
      name: 'highlights',
      type: 'array',
      fields: [{ name: 'text', type: 'text', required: true }],
      admin: { description: 'Short selling-point bullets.' },
    },
    { name: 'story', type: 'richText', admin: { description: 'Long-form marketing copy.' } },
    {
      name: 'badges',
      type: 'array',
      fields: [{ name: 'label', type: 'text', required: true }],
    },
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
