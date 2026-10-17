package com.example.docanalyzer.question;

import com.example.docanalyzer.document.*;
import com.example.docanalyzer.ingestion.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class QuestionServiceTest {
	@Test
	void abstainsWhenNoRelevantChunksExist() {
		DocumentRepository docs = mock(DocumentRepository.class);
		DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
		AiModelService ai = mock(AiModelService.class);
		UUID id = UUID.randomUUID();
		when(docs.findAllByOwnerIdOrderByCreatedAtDesc("alice")).thenReturn(List.of(new DocumentRecord(id, "alice",
				"notes.txt", "text/plain", 12, "checksum", "key", DocumentStatus.READY, Instant.now())));
		when(chunks.findAllByDocumentIdIn(List.of(id))).thenReturn(List.of());
		when(ai.provider()).thenReturn("ollama");
		QuestionService service = new QuestionService(docs, chunks, ai, 6, 0.08);

		QuestionResponse response = service.ask("alice",
				new QuestionRequest("What is the contract renewal date?", List.of()));

		assertEquals("INSUFFICIENT_EVIDENCE", response.status());
		verify(ai, never()).answer(anyString(), anyString());
	}

	@Test
	void answersWithRetrievedSourceContext() {
		DocumentRepository docs = mock(DocumentRepository.class);
		DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
		AiModelService ai = mock(AiModelService.class);
		UUID id = UUID.randomUUID();
		DocumentRecord doc = new DocumentRecord(id, "alice", "agreement.txt", "text/plain", 100, "checksum", "key",
				DocumentStatus.READY, Instant.now());
		when(docs.findAllByOwnerIdOrderByCreatedAtDesc("alice")).thenReturn(List.of(doc));
		when(chunks.findAllByDocumentIdIn(List.of(id))).thenReturn(List
				.of(new DocumentChunk(UUID.randomUUID(), id, 0, "The notice period is sixty days.", Instant.now())));
		when(ai.provider()).thenReturn("ollama");
		when(ai.answer(anyString(), anyString())).thenReturn("The notice period is sixty days [Source 1].");
		QuestionService service = new QuestionService(docs, chunks, ai, 6, 0.08);

		QuestionResponse response = service.ask("alice", new QuestionRequest("What is the notice period?", List.of()));

		assertEquals("ANSWERED", response.status());
		assertEquals(1, response.citations().size());
		assertEquals("agreement.txt", response.citations().get(0).filename());
		verify(ai).answer(anyString(), contains("The notice period is sixty days."));
	}
}
