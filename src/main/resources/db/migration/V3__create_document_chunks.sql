CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_doc_index
    ON document_chunks(document_id, chunk_index);