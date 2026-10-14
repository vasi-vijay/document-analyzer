CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(190) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    detected_media_type VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    checksum CHAR(64) NOT NULL,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_documents_owner_created ON documents(owner_id, created_at DESC);
CREATE INDEX idx_documents_checksum_owner ON documents(owner_id, checksum);
