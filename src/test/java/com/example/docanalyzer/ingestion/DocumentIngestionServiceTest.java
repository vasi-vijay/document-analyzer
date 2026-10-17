package com.example.docanalyzer.ingestion;

import com.example.docanalyzer.document.DocumentRepository;
import com.example.docanalyzer.document.DocumentStorageService;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DocumentIngestionServiceTest {
	@Test
	void splitsLongTextIntoOverlappingChunks() {
		var service = new DocumentIngestionService(mock(DocumentStorageService.class), mock(DocumentRepository.class),
				mock(DocumentChunkRepository.class), 100, 20, 10000);
		String text = "alpha ".repeat(100);
		var chunks = service.split(UUID.randomUUID(), text);
		assertTrue(chunks.size() > 1);
		assertTrue(chunks.stream().allMatch(c -> !c.getContent().isBlank()));
		assertTrue(chunks.get(0).getContent().length() <= 100);
	}

	@Test
	void emptyTextProducesNoChunks() {
		var service = new DocumentIngestionService(mock(DocumentStorageService.class), mock(DocumentRepository.class),
				mock(DocumentChunkRepository.class), 100, 20, 10000);
		assertTrue(service.split(UUID.randomUUID(), "  \n ").isEmpty());
	}
}
