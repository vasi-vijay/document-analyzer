# Document Analyzer — secure upload vertical slice

Java 21 / Spring Boot 3 foundation for a personal-first document Q&A product. This slice implements authenticated upload, Tika-based text extraction, bounded chunking, PostgreSQL chunk persistence, a lexical retrieval baseline, configurable Spring AI model calls (Ollama or OpenAI-compatible endpoints), grounded-question prompting, source excerpts, per-user document access, and a minimal Thymeleaf UI.

**Still not implemented:** OCR invocation, embeddings/vector retrieval, hybrid retrieval/reranking, background Kafka processing, Redis-backed rate limiting, robust citation entailment validation, tenant administration. The current retrieval is lexical (token overlap), not embedding-based semantic search. Kafka and Redis dependencies are configured but not used by the application yet.

## Pinned baseline

- Java 21
- Spring Boot 3.4.5
- Spring AI 1.0.0 BOM (OpenAI-compatible + Ollama starters)
- PostgreSQL 16 with pgvector, Flyway
- Apache Tika 3.2.2, PDFBox 3.0.5, Apache POI 5.4.1
- Kafka 4.1.0, Redis 7.4
- Bootstrap 5.3.3

These versions are pinned for reproducibility, not a claim that they are the newest available. Review vendor security advisories and upgrade pins before public deployment.

## Run locally

Requirements: JDK 21, Maven 3.9+, Docker Compose.

1. Copy `.env.example` to `.env`; set a strong `APP_SECURITY_PASSWORD`. Never commit `.env`.
2. Start the complete stack: `docker compose up --build -d` (or start only dependencies with `docker compose up -d postgres redis kafka` and run Maven locally).
3. If running Maven on the host, export environment values from `.env` in your shell (Compose does not automatically export values to a process started outside Compose). Set `APP_SECURITY_USERNAME`, `APP_SECURITY_PASSWORD`, and `POSTGRE_DB_PASSWORD` explicitly.
4. Start the app: `mvn spring-boot:run`
5. Open `http://localhost:8080/`. Authenticate with the configured username/password.
6. API health: `GET http://localhost:8080/actuator/health`

For convenience, `docker compose` reads `.env` automatically for substitutions, but your local Maven process does not. Keep application and database password values aligned.

## Configure the model endpoint

The model provider is selected with `APP_AI_PROVIDER`:

### Local Ollama (default)

```bash
export APP_AI_PROVIDER=ollama
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.2
ollama pull llama3.2
```

For an Ollama server on another machine, set `OLLAMA_BASE_URL` to its reachable URL, e.g. `http://192.168.1.20:11434`. Ensure that server is protected and not exposed publicly without authentication/TLS.

### Hosted OpenAI

```bash
export APP_AI_PROVIDER=openai
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_API_KEY=your-api-key
export OPENAI_MODEL=gpt-4o-mini
```

### OpenAI-compatible local/server endpoint

LM Studio, vLLM, or another compatible server can be selected using the OpenAI adapter:

```bash
export APP_AI_PROVIDER=openai
export OPENAI_BASE_URL=http://localhost:1234
export OPENAI_API_KEY=local-placeholder-if-required-by-server
export OPENAI_MODEL=your-loaded-model-id
```

Use the base URL expected by your server; Spring AI appends the OpenAI API path. If your server is exposed under a custom prefix, verify its compatibility with the Spring AI OpenAI client. The application can be restarted with different environment variables to switch providers and endpoints; no code change is required. Both model starters are present, but the application chooses only the configured provider for each answer.

## Analysis flow

1. `POST /api/v1/documents` authenticates the caller and validates the uploaded file.
2. Apache Tika extracts text, bounded by `APP_INGESTION_MAX_EXTRACTED_CHARS`.
3. Text is split into overlapping chunks and persisted by Flyway-managed PostgreSQL tables. Document status becomes `READY` when text was extracted, or `FAILED` when no readable text was found.
4. `POST /api/v1/questions` searches only ready documents owned by the authenticated user, optionally restricted by document IDs.
5. A lexical token-overlap baseline ranks chunks. If no relevant chunks are found, the service abstains without calling the model.
6. Spring AI calls the configured model with the question and retrieved source chunks. The prompt treats source text as untrusted data and instructs the model to avoid unsupported claims.
7. The response contains answer status, provider name, source excerpts and limitations.

The current chunk retrieval is a starter baseline; it is not a substitute for embedding-based semantic retrieval or a formal citation-entailment check. Scanned images/PDFs need OCR, which is not wired in yet. Upload parsing currently runs synchronously within the request and should move to a Kafka worker for larger files.

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

### Ask a question

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{"question":"Summarize the key points","documentIds":[]}' \
  http://localhost:8080/api/v1/questions
```

Pass document IDs to limit retrieval to selected documents. An empty list searches all ready documents owned by the current user.

## Test

```bash
mvn test
```

Tests are included for filename sanitization, chunk splitting, retrieval abstention, grounded source context, and unauthenticated API access. Maven tests have not been executed in this environment; run them locally and treat any build/test failure as unresolved until fixed. Add PostgreSQL Testcontainers integration tests before calling the upload path production-ready.

## Next milestones

1. Add upload/question integration tests with PostgreSQL and storage failure cleanup.
2. Move extraction/chunking to Kafka workers and add OCR for scanned files.
3. Add embeddings, pgvector storage, hybrid search, and reranking.
4. Improve scope detection, citation-entailment validation, and adversarial evaluation.
5. Add rate limits, quotas, observability, retention/deletion propagation, malware scanning, and deployment hardening.


## Configuration format and existing database

Configuration is in `src/main/resources/application.properties` (the YAML file has been removed). The PostgreSQL defaults preserve `jdbc:postgresql://localhost:5432/vasivijay`, username `docanalyzer`, and the environment variable `POSTGRE_DB_PASSWORD`. Do not put a real password in source control.

**Existing Flyway database warning:** This starter's migration SQL must match the migration history already applied to your `vasivijay` database. If that database already has migrations with versions 1 and 2 under different SQL contents, do not blindly run this archive against it: Flyway can report checksum mismatch or migration conflicts. Preserve your original applied migration files and add only new higher-numbered migrations after reconciling the schema.
