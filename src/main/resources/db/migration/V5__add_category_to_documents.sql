-- V5: Add category to documents for RAG narrowing.
-- When a user asks a health-related question with category=HEALTH_INSURANCE,
-- the vector search filters to only health insurance chunks.
-- This dramatically improves retrieval precision for large document sets.

ALTER TABLE documents ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'OTHER';

CREATE INDEX idx_documents_category ON documents (category);