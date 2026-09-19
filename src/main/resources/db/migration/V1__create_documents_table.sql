-- V1: Create documents table for ingested file metadata
CREATE TABLE IF NOT EXISTS documents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_name       VARCHAR(512) NOT NULL,
    stored_path     VARCHAR(1024) NOT NULL,
    mime_type       VARCHAR(256) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    chunk_count     INTEGER NOT NULL DEFAULT 0,
    tags            VARCHAR(1024),
    ingested_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_documents_ingested_at ON documents (ingested_at DESC);
CREATE INDEX idx_documents_deleted_at ON documents (deleted_at) WHERE deleted_at IS NULL;