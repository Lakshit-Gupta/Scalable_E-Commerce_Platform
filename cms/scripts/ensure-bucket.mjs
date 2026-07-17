// Ensure the CMS media bucket exists at container startup (v0.1.4). Mirrors product-service's
// BucketInitializer. Best-effort: always exits 0 so it never blocks boot. No-op when S3 is unset
// (local-disk media). Runs under Node with the @aws-sdk/client-s3 that @payloadcms/storage-s3 uses.
import { S3Client, HeadBucketCommand, CreateBucketCommand } from '@aws-sdk/client-s3'

const endpoint = process.env.STORAGE_ENDPOINT
const bucket = process.env.STORAGE_BUCKET

if (!endpoint || !bucket) {
  console.log('[cms-storage] STORAGE_* not set — skipping bucket ensure (local-disk media)')
  process.exit(0)
}

const s3 = new S3Client({
  endpoint,
  region: process.env.STORAGE_REGION || 'us-east-1',
  forcePathStyle: true,
  credentials: {
    accessKeyId: process.env.STORAGE_ACCESS_KEY || '',
    secretAccessKey: process.env.STORAGE_SECRET_KEY || ''
  }
})

for (let attempt = 1; attempt <= 10; attempt++) {
  try {
    await s3.send(new HeadBucketCommand({ Bucket: bucket }))
    console.log(`[cms-storage] bucket '${bucket}' present`)
    process.exit(0)
  } catch (headErr) {
    try {
      await s3.send(new CreateBucketCommand({ Bucket: bucket }))
      console.log(`[cms-storage] created bucket '${bucket}'`)
      process.exit(0)
    } catch (createErr) {
      console.warn(`[cms-storage] ensure attempt ${attempt}/10 failed: ${createErr.name || createErr}`)
      await new Promise((r) => setTimeout(r, 2000))
    }
  }
}
console.warn(`[cms-storage] giving up ensuring bucket '${bucket}' — uploads fail until storage is reachable`)
process.exit(0)
