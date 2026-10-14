package com.example.docanalyzer.document;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentUploadService {
	private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf",
			"application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/vnd.ms-excel",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"application/vnd.ms-powerpoint",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"text/plain", "text/markdown", "text/csv", "text/html",
			"application/json", "application/xml", "text/xml", "image/png",
			"image/jpeg", "image/tiff", "image/bmp", "image/webp",
			"message/rfc822");
	private final DocumentRepository repository;
	private final DocumentStorageService storage;
	private final Tika tika = new Tika();
	private final long maxBytes;

	public DocumentUploadService(DocumentRepository repository,
			DocumentStorageService storage,
			@Value("${app.upload.max-bytes}") long maxBytes) {
		this.repository = repository;
		this.storage = storage;
		this.maxBytes = maxBytes;
	}

	@Transactional
	public DocumentResponse upload(String ownerId, MultipartFile file)
			throws IOException {
		if (file == null || file.isEmpty())
			throw new UploadException("EMPTY_FILE",
					"The uploaded file is empty.");
		if (file.getSize() > maxBytes)
			throw new UploadException("FILE_TOO_LARGE",
					"The file exceeds the configured upload limit.");
		String original = sanitizeFilename(file.getOriginalFilename());
		if (original.isBlank())
			throw new UploadException("INVALID_FILENAME",
					"A valid filename is required.");

		String mediaType;
		try (InputStream in = file.getInputStream()) {
			mediaType = tika.detect(in, original).toLowerCase(Locale.ROOT);
		} catch (RuntimeException ex) {
			throw new UploadException("UNREADABLE_FILE",
					"The file could not be inspected.");
		}
		if (!ALLOWED_TYPES.contains(mediaType)) {
			throw new UploadException("UNSUPPORTED_FILE_TYPE",
					"This file type is not supported: " + mediaType);
		}

		String checksum = sha256(file);
		UUID id = UUID.randomUUID();
		String storageKey = storage.store(id, file);
		try {
			DocumentRecord saved = repository.save(new DocumentRecord(id,
					ownerId, original, mediaType, file.getSize(), checksum,
					storageKey, DocumentStatus.UPLOADED, Instant.now()));
			return DocumentResponse.from(saved);
		} catch (RuntimeException ex) {
			storage.delete(storageKey);
			throw ex;
		}
	}

	static String sanitizeFilename(String filename) {
		if (filename == null || filename.isBlank()) {
			return "";
		}

		// Normalize path separators and keep only the final filename.
		String name = filename.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).trim();

		// Reject empty names and dot-only names.
		if (name.isEmpty() || name.matches("\\.+")) {
			return "";
		}

		// Remove control characters such as newlines and tabs.
		name = name.replaceAll("\\p{Cc}", "");

		// Replace unsupported characters with underscores.
		name = name.replaceAll("[^a-zA-Z0-9._-]", "_");

		// Reject names that are empty or contain only dots.
		if (name.isBlank() || name.matches("\\.+")) {
			return "";
		}

		return name;
	}

	private String sha256(MultipartFile file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = file.getInputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1)
					digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
