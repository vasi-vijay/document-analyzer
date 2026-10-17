package com.example.docanalyzer.question;

import java.util.UUID;

public record SourceCitation(UUID documentId, String filename, int chunkIndex, String excerpt) {
}
