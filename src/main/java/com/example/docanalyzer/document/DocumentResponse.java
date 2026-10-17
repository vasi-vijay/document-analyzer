package com.example.docanalyzer.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, String filename, String mediaType, long sizeBytes, String checksum,
		DocumentStatus status, Instant createdAt) {
	public static DocumentResponse from(DocumentRecord d) {
		return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getDetectedMediaType(), d.getSizeBytes(),
				d.getChecksum(), d.getStatus(), d.getCreatedAt());
	}
}
