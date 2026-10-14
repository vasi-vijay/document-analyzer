package com.example.docanalyzer.document;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadServiceTest {
	@Test
	void stripsPathTraversalFromFilename() {
		assertEquals("contract.pdf", DocumentUploadService.sanitizeFilename("../../contract.pdf"));
		assertEquals("contract.pdf", DocumentUploadService.sanitizeFilename("C:\\\\temp\\\\contract.pdf"));
	}

	@Test
	void rejectsBlankAndDotFilenames() {
		assertEquals("", DocumentUploadService.sanitizeFilename(null));
		assertEquals("", DocumentUploadService.sanitizeFilename(".."));
		assertEquals("", DocumentUploadService.sanitizeFilename(" . "));
	}

	@Test
	void sanitizesControlAndUnsafeCharacters() {
		assertEquals("my_file_.pdf", DocumentUploadService.sanitizeFilename("my file?.pdf\n"));
	}

}
