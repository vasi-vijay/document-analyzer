package com.example.docanalyzer.ingestion;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_chunks", indexes = @Index(name = "idx_document_chunks_doc_index", columnList = "document_id,chunk_index"))
public class DocumentChunk {
	@Id
	private UUID id;
	@Column(name = "document_id", nullable = false)
	private UUID documentId;
	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;
	@Column(nullable = false, columnDefinition = "text")
	private String content;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected DocumentChunk() {
	}

	public DocumentChunk(UUID id, UUID documentId, int chunkIndex, String content, Instant createdAt) {
		this.id = id;
		this.documentId = documentId;
		this.chunkIndex = chunkIndex;
		this.content = content;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public int getChunkIndex() {
		return chunkIndex;
	}

	public String getContent() {
		return content;
	}
}
