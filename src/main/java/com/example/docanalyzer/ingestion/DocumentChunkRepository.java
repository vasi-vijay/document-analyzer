package com.example.docanalyzer.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
	
	List<DocumentChunk> findAllByDocumentIdIn(List<UUID> documentIds);

	void deleteAllByDocumentId(UUID documentId);
}
