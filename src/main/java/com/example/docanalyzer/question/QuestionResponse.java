package com.example.docanalyzer.question;

import java.util.List;

public record QuestionResponse(String status, String answer, String provider, List<SourceCitation> citations,
		List<String> limitations) {
}
