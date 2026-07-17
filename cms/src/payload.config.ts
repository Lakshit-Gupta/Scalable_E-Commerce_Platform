import { postgresAdapter } from '@payloadcms/db-postgres'
import { lexicalEditor } from '@payloadcms/richtext-lexical'
import { s3Storage } from '@payloadcms/storage-s3'
import path from 'path'
import { buildConfig, type Plugin } from 'payload'
import { fileURLToPath } from 'url'
import sharp from 'sharp'

import { Users } from './collections/Users'
import { Media } from './collections/Media'
import { Pages } from './collections/Pages'
import { Posts } from './collections/Posts'
import { ProductEnrichment } from './collections/ProductEnrichment'

const filename = fileURLToPath(import.meta.url)
const dirname = path.dirname(filename)

// Origins allowed to call the CMS REST/GraphQL API from a browser and submit login forms.
// The storefront SSR/BFF calls server-to-server (no CORS needed) but we allow its public origin too.
const corsOrigins = (process.env.CMS_CORS_ORIGINS || 'http://localhost:3000')
  .split(',')
  .map((o) => o.trim())
  .filter(Boolean)

// Object storage for Media uploads (v0.1.4). When STORAGE_* is configured (MinIO locally / R2 in
// prod — same S3 API as product-service, path-style), the Media collection is backed by S3 and its
// files stream through Payload (bucket stays private). Unset → Payload falls back to local disk (dev).
const plugins: Plugin[] = []
if (process.env.STORAGE_ENDPOINT && process.env.STORAGE_BUCKET) {
  plugins.push(
    s3Storage({
      collections: { media: true },
      bucket: process.env.STORAGE_BUCKET,
      config: {
        endpoint: process.env.STORAGE_ENDPOINT,
        region: process.env.STORAGE_REGION || 'us-east-1',
        forcePathStyle: true, // required by MinIO / R2
        credentials: {
          accessKeyId: process.env.STORAGE_ACCESS_KEY || '',
          secretAccessKey: process.env.STORAGE_SECRET_KEY || ''
        }
      }
    })
  )
}

export default buildConfig({
  serverURL: process.env.CMS_PUBLIC_URL || 'http://localhost:3002',
  admin: {
    user: Users.slug,
    importMap: {
      baseDir: path.resolve(dirname),
    },
  },
  collections: [Pages, Posts, ProductEnrichment, Media, Users],
  editor: lexicalEditor(),
  secret: process.env.PAYLOAD_SECRET || '',
  cors: corsOrigins,
  csrf: corsOrigins,
  typescript: {
    outputFile: path.resolve(dirname, 'payload-types.ts'),
  },
  db: postgresAdapter({
    // Dev convenience: auto-create/sync the schema on boot (mirrors the platform's
    // ddl-auto=update dev convention). In prod (NODE_ENV=production) push is off and the
    // container applies committed migrations from migrationDir instead (Flyway analog).
    push: process.env.CMS_DB_PUSH !== 'false',
    migrationDir: path.resolve(dirname, 'migrations'),
    pool: {
      connectionString: process.env.DATABASE_URL || '',
    },
  }),
  sharp,
  plugins,
})
