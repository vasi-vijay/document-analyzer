# Document Analyzer — secure upload vertical slice

Java 21 / Spring Boot 3 foundation for a personal-first document Q&A product. This first slice implements authenticated upload, content-type sniffing, bounded file sizes, generated storage keys, SHA-256 checksums, per-user document listing, ownership-checked reads/deletes, PostgreSQL metadata, and a minimal Thymeleaf UI.

**Not yet implemented:** ingestion workers, text extraction pipeline, OCR invocation, chunking, embeddings, vector/lexical retrieval, grounded Q&A, citation validation, Kafka job publishing, Redis-backed rate limiting, tenant administration. Dependencies/configuration are staged for these milestones; their presence does not mean those capabilities are already active.

## Pinned baseline

- Java 21
- Spring Boot 3.4.5
- Spring AI 1.0.0 BOM (OpenAI + Ollama starters)
- PostgreSQL 16 with pgvector, Flyway
- Apache Tika 3.2.2, PDFBox 3.0.5, Apache POI 5.4.1
- Kafka 4.1.0, Redis 7.4
- Bootstrap 5.3.3

These versions are pinned for reproducibility, not a claim that they are the newest available. Review vendor security advisories and upgrade pins before public deployment.

## Run locally

Requirements: JDK 21, Maven 3.9+, Docker Compose.

1. Copy `.env.example` to `.env`; set a strong `APP_SECURITY_PASSWORD`. Never commit `.env`.
2. Start the complete stack: `docker compose up --build -d` (or start only dependencies with `docker compose up -d postgres redis kafka` and run Maven locally).
3. If running Maven on the host, export environment values from `.env` in your shell (Compose does not automatically export values to a process started outside Compose). Set `APP_SECURITY_USERNAME`, `APP_SECURITY_PASSWORD`, and `SPRING_DATASOURCE_PASSWORD` explicitly.
4. Start the app: `mvn spring-boot:run`
5. Open `http://localhost:8080/`. Authenticate with the configured username/password.
6. API health: `GET http://localhost:8080/actuator/health`

For convenience, `docker compose` reads `.env` automatically for substitutions, but your local Maven process does not. Keep application and database password values aligned.

## API examples

```bash
# Upload; credentials are sent using HTTP Basic over localhost only.
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -F "file=@./sample.pdf" http://localhost:8080/api/v1/documents

# List only documents owned by the authenticated user
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  http://localhost:8080/api/v1/documents

# Inspect one document
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  http://localhost:8080/api/v1/documents/<document-uuid>
```

Use HTTPS and a proper secret manager outside local development. HTTP Basic is a simple first-slice authentication mechanism; before multi-user production, add durable users, session/token lifecycle, CSRF-appropriate browser auth, audit logs, quotas, and account recovery.

## Supported upload types in this slice

PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, plain text, Markdown, CSV, HTML, JSON, XML, PNG, JPEG, TIFF, BMP, WebP, and EML, subject to parser detection and the allowlist. Archives are intentionally rejected in this first slice until safe decompression limits and malware scanning are added. Office macros are not executed.

MIME sniffing is a useful control but is not malware scanning or proof that a file is safe. Run parsers with strict resource limits and consider antivirus scanning before accepting untrusted public uploads.

## Data and migration notes

Flyway creates the `documents` table and enables the pgvector extension. The extension is available in the supplied `pgvector/pgvector` image. Embedding tables and indexes should be added in a later migration when the embedding dimensions/model are selected. Hibernate schema management is set to `validate`; Flyway owns schema changes.

The initial identity is an in-memory configured user. The `owner_id` is the authenticated username, so documents are isolated by user in queries, but user administration is not yet production-ready. The local storage implementation uses opaque UUID storage keys and never resolves paths from client filenames.

## Test

```bash
mvn test
```

Tests are included for filename sanitization and unauthenticated API access. I have not executed Maven tests in this environment; run them locally and treat any build/test failure as unresolved until fixed. Add PostgreSQL Testcontainers integration tests before calling the upload path production-ready.

## Next milestones

1. Add upload integration tests with PostgreSQL/Testcontainers and storage failure cleanup.
2. Publish ingestion jobs to Kafka transactionally via an outbox pattern.
3. Parse documents with Tika/PDFBox/POI, OCR scanned pages, and preserve source coordinates.
4. Chunk, embed with local Ollama or hosted embeddings, store vectors, and add hybrid search.
5. Implement evidence sufficiency checks, source-verified citations, scope/abstention policy, prompt-injection defenses, and adversarial evaluation.
6. Add rate limits, quotas, observability, retention/deletion propagation, malware scanning, and deployment hardening.
