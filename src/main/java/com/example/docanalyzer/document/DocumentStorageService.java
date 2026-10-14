package com.example.docanalyzer.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class DocumentStorageService {
	private final Path root;

	public DocumentStorageService(@Value("${app.storage.root}") String root)
			throws IOException {
		this.root = Paths.get(root).toAbsolutePath().normalize();
		Files.createDirectories(this.root);
	}

	public String store(UUID id, MultipartFile file) throws IOException {
		String key = id + ".blob";
		Path destination = root.resolve(key).normalize();
		if (!destination.getParent().equals(root))
			throw new IOException("Invalid storage destination");
		Path temp = Files.createTempFile(root, id.toString(), ".upload");
		try {
			try (var in = file.getInputStream()) {
				Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			}
			try {
				Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temp, destination,
						StandardCopyOption.REPLACE_EXISTING);
			}
			return key;
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	public void delete(String key) throws IOException {
		Path path = root.resolve(key).normalize();
		if (!path.getParent().equals(root))
			throw new IOException("Invalid storage key");
		Files.deleteIfExists(path);
	}
}
