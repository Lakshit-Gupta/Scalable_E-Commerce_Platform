-- Enable pgvector extension in recommendations_db for Phase 2 embedding-based similarity.
-- Phase 2 implementation uses item-based CF (co-purchase aggregates from Redis).
-- pgvector is ready for future ML embedding columns (product_embeddings table).
\c recommendations_db
CREATE EXTENSION IF NOT EXISTS vector;
