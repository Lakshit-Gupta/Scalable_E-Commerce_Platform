import type { CollectionConfig } from 'payload'

// Blog / editorial posts. Storefront renders /blog and /blog/[slug] from the public REST API.
export const Posts: CollectionConfig = {
  slug: 'posts',
  admin: {
    useAsTitle: 'title',
    defaultColumns: ['title', 'slug', 'status', 'publishedAt'],
  },
  access: {
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
    },
    { name: 'excerpt', type: 'textarea' },
    {
      name: 'coverImage',
      type: 'upload',
      relationTo: 'media',
    },
    { name: 'content', type: 'richText' },
    { name: 'author', type: 'text' },
    { name: 'publishedAt', type: 'date' },
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
