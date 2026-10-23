package com.example.docanalyzer.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.docanalyzer.document.DocumentRecord;
import com.example.docanalyzer.document.DocumentRepository;
import com.example.docanalyzer.document.DocumentStatus;
import com.example.docanalyzer.document.DocumentStorageService;

@Service
public class DocumentIngestionService {
	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

	private final DocumentStorageService storage;
	private final DocumentRepository documents;
	private final DocumentChunkRepository chunks;
	private final int chunkSize;
	private final int overlap;
	private final int maxExtractedChars;

	public DocumentIngestionService(DocumentStorageService storage, DocumentRepository documents,
			DocumentChunkRepository chunks, @Value("${app.ingestion.chunk-size:1200}") int chunkSize,
			@Value("${app.ingestion.chunk-overlap:180}") int overlap,
			@Value("${app.ingestion.max-extracted-chars:2000000}") int maxExtractedChars) {
		if (chunkSize < 100 || overlap < 0 || overlap >= chunkSize)
			throw new IllegalArgumentException("Invalid chunk configuration");
		this.storage = storage;
		this.documents = documents;
		this.chunks = chunks;
		this.chunkSize = chunkSize;
		this.overlap = overlap;
		this.maxExtractedChars = maxExtractedChars;
	}

	@Transactional
	public void ingest(DocumentRecord document) throws IOException {
		try (InputStream input = storage.open(document.getStorageKey())) {
			BodyContentHandler handler = new BodyContentHandler(maxExtractedChars);
			Metadata metadata = new Metadata();
			ParseContext context = new ParseContext();
			byte[] header;
			String text;
			try (InputStream probe = storage.open(document.getStorageKey())) {
				header = probe.readNBytes(16);
			}

			log.info("Ingestion probe: documentId={}, storageKey={}, headerHex={}", document.getId(),
					document.getStorageKey(), java.util.HexFormat.of().formatHex(header));

			if ("application/pdf".equals(document.getDetectedMediaType())) {
				text = extractPdfText(input);
			} else {
				try {
					new AutoDetectParser().parse(input, handler, metadata, context);
				} catch (org.xml.sax.SAXException ex) {
					// Do not silently treat every parsing exception as
					// a content-limit exception.
					if (handler.toString().isBlank() || !isContentLimitException(ex)) {
						throw ex;
					}
					// Keep the extracted prefix when the output limit
					// was reached.
				}
				text = handler.toString();
			}

			if (text.isBlank()) {
				document.setStatus(DocumentStatus.FAILED);
				documents.save(document);
				return;
			}
			if (text.length() > maxExtractedChars) {
				text = text.substring(0, maxExtractedChars);
			}
			chunks.deleteAllByDocumentId(document.getId());
			List<DocumentChunk> batch = split(document.getId(), text);
			chunks.saveAll(batch);
			document.setStatus(DocumentStatus.READY);
			documents.save(document);
		} catch (IOException | org.apache.tika.exception.TikaException | org.xml.sax.SAXException
				| RuntimeException ex) {
			log.error("Document ingestion failed. documentId={}, storageKey={}", document.getId(),
					document.getStorageKey(), ex);
			document.setStatus(DocumentStatus.FAILED);
			documents.save(document);
		}
	}

	private String extractPdfText(InputStream input) throws IOException {
		byte[] pdfBytes = input.readAllBytes();
		try (var pdf = Loader.loadPDF(pdfBytes)) {
			return new PDFTextStripper().getText(pdf);
		}
	}

	private boolean isContentLimitException(org.xml.sax.SAXException ex) {
		Throwable cause = ex;
		while (cause != null) {
			String message = cause.getMessage();
			if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("write limit")) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	List<DocumentChunk> split(UUID documentId, String text) {
		List<DocumentChunk> result = new ArrayList<>();
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
		if (normalized.isEmpty())
			return result;
		int start = 0, index = 0;
		while (start < normalized.length()) {
			int end = Math.min(normalized.length(), start + chunkSize);
			if (end < normalized.length()) {
				int boundary = normalized.lastIndexOf('\n', end);
				if (boundary > start + chunkSize / 2)
					end = boundary;
			}
			String content = normalized.substring(start, end).trim();
			if (!content.isBlank())
				result.add(new DocumentChunk(UUID.randomUUID(), documentId, index++, content, Instant.now()));
			if (end >= normalized.length())
				break;
			start = Math.max(start + 1, end - overlap);
		}
		return result;
	}
}
