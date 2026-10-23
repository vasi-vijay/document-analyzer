package com.example.docanalyzer.document;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

	private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

	private final DocumentUploadService uploadService;
	private final DocumentRepository repository;
	private final DocumentStorageService storage;

	public DocumentController(DocumentUploadService uploadService, DocumentRepository repository,
			DocumentStorageService storage) {
		this.uploadService = uploadService;
		this.repository = repository;
		this.storage = storage;
	}

	@PostMapping(consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse upload(@RequestPart("file") MultipartFile file, Authentication auth) throws IOException {
		long startTime = System.nanoTime();
		String extension = getExtension(file.getOriginalFilename());
		log.info("Document upload started. extension={}, sizeBytes={}, " + "declaredContentType={}", extension,
				file.getSize(), file.getContentType());
		try {
			DocumentResponse response = uploadService.upload(auth.getName(), file);
			log.info("Document upload completed successfully. " + "extension={}, sizeBytes={}, durationMs={}",
					extension, file.getSize(), elapsedMs(startTime));
			return response;
		} catch (IOException | RuntimeException ex) {
			log.error("Document upload failed. extension={}, sizeBytes={}, " + "durationMs={}, exceptionType={}",
					extension, file.getSize(), elapsedMs(startTime), ex.getClass().getSimpleName(), ex);
			throw ex;
		}
	}

	@GetMapping
	public List<DocumentResponse> list(Authentication auth) {
		List<DocumentResponse> documents = repository.findAllByOwnerIdOrderByCreatedAtDesc(auth.getName()).stream()
				.map(DocumentResponse::from).toList();
		log.debug("Document list retrieved successfully. count={}", documents.size());
		return documents;
	}

	@GetMapping("/{id}")
	public DocumentResponse get(@PathVariable UUID id, Authentication auth) {
		return repository.findByIdAndOwnerId(id, auth.getName()).map(record -> {
			log.debug("Document retrieved successfully. documentId={}", id);
			return DocumentResponse.from(record);
		}).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id, Authentication auth) throws IOException {
		long startTime = System.nanoTime();
		DocumentRecord record = repository.findByIdAndOwnerId(id, auth.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
		try {
			storage.delete(record.getStorageKey());
			repository.delete(record);
			log.info("Document deleted successfully. documentId={}, " + "durationMs={}", id, elapsedMs(startTime));
		} catch (IOException | RuntimeException ex) {
			log.error("Document deletion failed. documentId={}, " + "durationMs={}, exceptionType={}", id,
					elapsedMs(startTime), ex.getClass().getSimpleName(), ex);
			throw ex;
		}
	}

	private static long elapsedMs(long startTime) {
		return (System.nanoTime() - startTime) / 1_000_000;
	}

	private static String getExtension(String filename) {
		if (filename == null || filename.isBlank()) {
			return "unknown";
		}
		int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
		String name = filename.substring(slash + 1);
		int dot = name.lastIndexOf('.');

		if (dot <= 0 || dot == name.length() - 1) {
			return "unknown";
		}
		return name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
