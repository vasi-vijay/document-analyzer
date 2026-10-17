package com.example.docanalyzer.document;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents", indexes = { @Index(name = "idx_documents_owner_created", columnList = "owner_id,created_at"),
		@Index(name = "idx_documents_checksum_owner", columnList = "owner_id,checksum") })
public class DocumentRecord {
	@Id
	private UUID id;

	@Column(name = "owner_id", nullable = false, length = 190)
	private String ownerId;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "detected_media_type", nullable = false, length = 160)
	private String detectedMediaType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(nullable = false, length = 64)
	private String checksum;

	@Column(name = "storage_key", nullable = false, unique = true, length = 80)
	private String storageKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private DocumentStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected DocumentRecord() {
	}

	public DocumentRecord(UUID id, String ownerId, String originalFilename, String detectedMediaType, long sizeBytes,
			String checksum, String storageKey, DocumentStatus status, Instant createdAt) {
		this.id = id;
		this.ownerId = ownerId;
		this.originalFilename = originalFilename;
		this.detectedMediaType = detectedMediaType;
		this.sizeBytes = sizeBytes;
		this.checksum = checksum;
		this.storageKey = storageKey;
		this.status = status;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getDetectedMediaType() {
		return detectedMediaType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getChecksum() {
		return checksum;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setStatus(DocumentStatus status) {
		this.status = status;
	}
}
