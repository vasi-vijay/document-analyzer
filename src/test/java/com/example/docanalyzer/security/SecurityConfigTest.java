package com.example.docanalyzer.security;

import com.example.docanalyzer.document.DocumentController;
import com.example.docanalyzer.document.DocumentRepository;
import com.example.docanalyzer.document.DocumentStorageService;
import com.example.docanalyzer.document.DocumentUploadService;
import com.example.docanalyzer.ingestion.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = { "app.security.username=tester", "app.security.password=test-password" })
class SecurityConfigTest {

	@Autowired
	MockMvc mvc;

	@MockBean
	DocumentUploadService uploadService;

	@MockBean
	DocumentIngestionService ingestionService;

	@MockBean
	DocumentRepository repository;

	@MockBean
	DocumentStorageService storage;

	@Test
	void documentApiRequiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
	}
}
