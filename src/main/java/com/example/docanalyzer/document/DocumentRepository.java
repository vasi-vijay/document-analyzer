package com.example.docanalyzer.document;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository
		extends
			JpaRepository<DocumentRecord, UUID> {
	List<DocumentRecord> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);
	Optional<DocumentRecord> findByIdAndOwnerId(UUID id, String ownerId);
}
