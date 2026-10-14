package com.example.docanalyzer.document;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentUploadService uploadService;
    private final DocumentRepository repository;
    private final DocumentStorageService storage;

    public DocumentController(DocumentUploadService uploadService, DocumentRepository repository,
                              DocumentStorageService storage) {
        this.uploadService = uploadService; this.repository = repository; this.storage = storage;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file, Authentication auth) throws IOException {
        return uploadService.upload(auth.getName(), file);
    }

    @GetMapping
    public List<DocumentResponse> list(Authentication auth) {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(auth.getName()).stream()
                .map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id, Authentication auth) {
        return repository.findByIdAndOwnerId(id, auth.getName()).map(DocumentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) throws IOException {
        DocumentRecord record = repository.findByIdAndOwnerId(id, auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        storage.delete(record.getStorageKey());
        repository.delete(record);
    }
}
