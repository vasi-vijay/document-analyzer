# Document Analyzer — Secure Upload Vertical Slice

A Java 21 / Spring Boot 3 foundation for a personal-first document Q&A application. The application supports authenticated document uploads, Apache Tika text extraction, bounded text chunking, PostgreSQL persistence, lexical retrieval, configurable Spring AI model calls, grounded question answering, source excerpts, per-user document isolation, and a minimal Thymeleaf web UI styled with Bootstrap.

The current implementation is a functional vertical slice, not a production-ready multi-tenant RAG platform.

**Not yet implemented:** OCR execution, embeddings and vector retrieval, hybrid search and reranking, asynchronous Kafka ingestion, Redis-backed rate limiting, formal citation-entailment validation, and tenant administration.

The retrieval implementation uses lexical token overlap. It does not yet use embedding-based semantic search.

## Features

- Authenticated document upload and listing
- Apache Tika-based text extraction
- Bounded, overlapping text chunking
- PostgreSQL document and chunk persistence
- Flyway-managed schema migrations
- Lexical retrieval over ready documents
- Configurable Ollama or OpenAI-compatible model providers
- Grounded-question prompting with source excerpts
- Per-user document access restrictions
- Thymeleaf-based web UI
- Bootstrap 5.3.3 styling baseline
- Spring Boot Actuator health endpoint
- Configurable ingestion limits and model settings

## Pinned technology baseline

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring AI BOM | 1.0.0 |
| PostgreSQL | 16 |
| Apache Tika | 3.2.2 |
| Apache PDFBox | 3.0.5 |
| Apache POI | 5.4.1 |
| Kafka | 4.1.0 |
| Redis | 7.4 |
| Bootstrap | 5.3.3 |
| Flyway | Version managed by the project configuration |
| pgvector | Supplied through the configured PostgreSQL image |

These pins support reproducibility; they are not a claim that these are the newest versions. Review compatibility and security advisories before upgrading or deploying publicly.

## Architecture

```text
Browser
   |
   v
Thymeleaf UI / REST API
   |
   v
Spring Security
   |
   v
Document upload and question services
   |
   +--> Apache Tika --> Text extraction
   |                       |
   |                       v
   |                 Chunking service
   |                       |
   |                       v
   |                  PostgreSQL
   |
   +--> Lexical retrieval --> Relevant chunks
                                  |
                                  v
                            Spring AI model
                            /             \
                       Ollama       OpenAI-compatible
                                  |
                                  v
                         Answer + source excerpts
```

PostgreSQL and Flyway provide persistence and schema management. Kafka and Redis are included in the broader technology baseline but are not yet used for asynchronous ingestion or distributed rate limiting.

## Requirements

- JDK 21
- Maven 3.9+
- Docker with Docker Compose
- PostgreSQL, provided through Docker Compose or a local installation
- Ollama for local model inference, or credentials for a hosted model endpoint

## Run locally

### 1. Configure environment variables

Copy the example environment file:

```bash
cp .env.example .env
```

Set a strong `APP_SECURITY_PASSWORD` and the other required values. Never commit `.env` or real credentials.

Example local configuration:

```dotenv
APP_SECURITY_USERNAME=admin
APP_SECURITY_PASSWORD=replace-with-a-strong-password

POSTGRE_DB_PASSWORD=replace-with-your-database-password

APP_AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5-coder:7b
```

Use the actual username and database password configured for your existing PostgreSQL instance. The example values above are placeholders, not credentials to copy unchanged.

### 2. Start the services

To start the complete Compose stack:

```bash
docker compose up --build -d
```

Alternatively, start only the dependencies:

```bash
docker compose up -d postgres redis kafka
```

Then run the application from your host.

Use the service names and profiles defined in your actual Compose file if they differ from these examples.

### 3. Start the Spring Boot application

When running Maven on the host, export the required variables into that shell. Docker Compose reads `.env` for Compose interpolation, but does not automatically export those variables to a separate Maven process.

For example, load the environment file in a compatible shell:

```bash
set -a
source .env
set +a
```

Then start the application:

```bash
mvn spring-boot:run
```

Ensure the application and PostgreSQL use matching credentials.

### 4. Open the web UI

Open:

**http://localhost:8080/**

Authenticate using the configured application username and password.

The root URL is the documented UI entry point. Confirm any additional page paths from the current controller mappings rather than assuming that every API endpoint has a corresponding UI page.

### 5. Check application health

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  http://localhost:8080/actuator/health
```

The expected response is a JSON health document. Its status depends on the application's configured health indicators and dependency availability.

## Web UI configuration

### UI technology

| Setting | Current baseline |
|---|---|
| Server-side rendering | Thymeleaf |
| UI styling | Bootstrap 5.3.3 |
| Local entry point | `http://localhost:8080/` |
| Application authentication | Spring Security with configured credentials |
| Document processing | Existing document upload and ingestion services |
| Question answering | Existing question API and model provider configuration |

Thymeleaf renders the application pages on the server. Bootstrap provides the styling baseline for responsive layouts and common form components.

### UI workflow

The intended user workflow is:

1. Open the application and authenticate.
2. Upload a supported document.
3. Allow the application to extract text and create searchable chunks.
4. Select a document, if selection is supported by the current page.
5. Enter a question about the available document content.
6. Review the answer, source excerpts, and any abstention or limitation information returned by the application.

The REST API remains available independently of the UI.

### UI-related configuration

The main application configuration lives in:

`src/main/resources/application.properties`

The current configuration uses environment variables for security, model selection, database connectivity, and ingestion limits. No separate UI-specific environment variable names are established in this README.

Do not introduce new UI configuration keys unless they are implemented in the application. If the templates use configurable properties for titles, upload limits, or display options, document their exact names alongside the corresponding controller and template code.

### UI development notes

- Keep Thymeleaf templates under the application's configured template directory, typically `src/main/resources/templates/`.
- Keep static CSS, JavaScript, and local UI assets under the configured static-resource directory, typically `src/main/resources/static/`.
- Use server-side validation and display understandable validation errors.
- Render extracted document text as escaped text, not executable HTML.
- Do not rely on hidden UI controls as a security boundary. The server must authorize every document operation.
- Protect browser form submissions against CSRF when using session-based browser authentication.
- Verify how Spring Security handles authentication and CSRF for the actual UI and REST endpoints before adding JavaScript-based API calls.

These directory conventions are standard Spring Boot conventions; confirm the actual project layout before adding files.

## Model provider configuration

The model provider is selected with `APP_AI_PROVIDER`.

### Local Ollama

The local development setup uses the `qwen2.5-coder:7b` model.

```bash
export APP_AI_PROVIDER=ollama
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=qwen2.5-coder:7b
```

Confirm that the model is available locally:

```bash
ollama list
```

If it is not installed, pull it:

```bash
ollama pull qwen2.5-coder:7b
```

The corresponding Spring AI configuration should resolve the model from the environment variable, with the local model as the fallback:

```properties
spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}
spring.ai.ollama.chat.options.model=${OLLAMA_MODEL:qwen2.5-coder:7b}
```

The configured model must exist on the Ollama server. A mismatch between the configured model name and the installed model can cause question requests to fail.

For Ollama on another machine, configure `OLLAMA_BASE_URL` with the reachable server URL. Do not expose an unprotected Ollama endpoint to the public internet.

### Hosted OpenAI

```bash
export APP_AI_PROVIDER=openai
export OPENAI_BASE_URL=https://api.openai.com
export OPENAI_API_KEY=your-api-key
export OPENAI_MODEL=gpt-4o-mini
```

Keep the API key secret and out of source control.

### OpenAI-compatible server

For a compatible endpoint such as LM Studio or vLLM:

```bash
export APP_AI_PROVIDER=openai
export OPENAI_BASE_URL=http://localhost:1234
export OPENAI_API_KEY=local-placeholder-if-required-by-server
export OPENAI_MODEL=your-loaded-model-id
```

Use the base URL and authentication settings expected by the server. Verify that its API paths and response format are compatible with the Spring AI OpenAI client.

Restart the application after changing provider settings. The application should use only the configured provider for a given run.

## Configuration reference

The following environment variables are used by the documented setup. Check `application.properties` and the Compose configuration for the exact defaults and any additional required variables.

| Variable | Purpose |
|---|---|
| `APP_SECURITY_USERNAME` | Configured application username |
| `APP_SECURITY_PASSWORD` | Configured application password |
| `POSTGRE_DB_PASSWORD` | PostgreSQL password |
| `APP_AI_PROVIDER` | Selects the model provider |
| `OLLAMA_BASE_URL` | Ollama server endpoint |
| `OLLAMA_MODEL` | Ollama model identifier |
| `OPENAI_BASE_URL` | Hosted or compatible OpenAI endpoint |
| `OPENAI_API_KEY` | API key for the selected OpenAI-compatible endpoint |
| `OPENAI_MODEL` | Model identifier for the OpenAI adapter |
| `APP_INGESTION_MAX_EXTRACTED_CHARS` | Maximum extracted text size, subject to the implementation's validation and default |

PostgreSQL's current local connection defaults are:

```text
JDBC URL: jdbc:postgresql://localhost:5432/ai-analyze
Username: docanalyzer
Password: supplied through POSTGRE_DB_PASSWORD
```

Do not put real credentials in application source files or version control.

## Analysis and ingestion flow

1. `POST /api/v1/documents` authenticates the caller and validates the upload.
2. Apache Tika extracts readable text, subject to the configured extraction limit.
3. The chunking service splits the text into bounded, overlapping chunks.
4. PostgreSQL persists the document and its chunks through the application's repository layer.
5. The document is marked `READY` when usable text is extracted, or `FAILED` when extraction produces no usable text, subject to the service's failure-handling behavior.
6. `POST /api/v1/questions` retrieves only ready documents belonging to the authenticated user, optionally restricted by document IDs.
7. Lexical token-overlap retrieval ranks candidate chunks.
8. If no relevant chunks are found, the service can abstain without calling the model.
9. Spring AI sends the question and retrieved source context to the configured model.
10. The response includes answer status, provider information, source excerpts, and applicable limitations.

The current ingestion path is synchronous. Large uploads can occupy request-processing resources and should eventually be handled by a background worker.

The chunking and retrieval pipeline also requires duplicate-content regression tests. Repeated text can reduce retrieval quality and cause redundant source excerpts. Verify whether duplication originates in the original file, text extraction, or chunk persistence before modifying existing stored chunks.

## REST API examples

### Upload a document

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -F "file=@./sample.pdf" \
  http://localhost:8080/api/v1/documents
```

The multipart field name is `file`.

### List documents

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  http://localhost:8080/api/v1/documents
```

The endpoint should return only documents accessible to the authenticated user.

### Inspect a document

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  "http://localhost:8080/api/v1/documents/<document-uuid>"
```

Replace `<document-uuid>` with a real document identifier.

### Ask a question

```bash
curl -u "$APP_SECURITY_USERNAME:$APP_SECURITY_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{
    "question": "Summarize the key points",
    "documentIds": []
  }' \
  http://localhost:8080/api/v1/questions
```

An empty `documentIds` list searches all ready documents owned by the authenticated user. Provide document IDs to restrict retrieval to selected documents.

The exact response fields depend on the application's response DTOs. Use the current API implementation as the source of truth for response schemas.

## Supported upload types

The current allowlist is intended to cover:

- PDF
- DOC and DOCX
- XLS and XLSX
- PPT and PPTX
- Plain text, Markdown, CSV
- HTML, JSON, XML
- PNG, JPEG, TIFF, BMP, WebP
- EML

Actual acceptance depends on the implemented allowlist, parser detection, and validation logic.

Archives are intentionally rejected in this first slice until safe decompression limits and malware scanning are implemented. Office macros are not executed.

MIME detection is not malware scanning and does not prove that a file is safe. Public deployments need strict parser resource limits and an appropriate malware-scanning strategy.

Scanned PDFs and images require OCR. OCR invocation is not implemented in the current slice.

## Security and access control

The current starter uses a configured in-memory identity. The authenticated username is stored as the document `owner_id`, and document queries are scoped to that identity.

This is a useful first-step access boundary, but it is not a complete multi-user identity platform.

Before public deployment:

- Use HTTPS.
- Store secrets in a suitable secret manager.
- Replace or extend the configured in-memory user with durable identity management.
- Review CSRF protection for browser requests.
- Add audit logging, quotas, rate limiting, and account lifecycle management.
- Enforce authorization on every document operation.
- Limit upload size, parser time, memory use, and extracted text length.
- Add malware scanning and a retention/deletion policy.
- Treat extracted content as untrusted data, including possible prompt-injection instructions.
- Validate that generated claims are supported by cited source passages.

HTTP Basic is acceptable as a simple local-development authentication mechanism over a protected connection. It should not be treated as a complete production authentication design.

## Database and migration notes

Flyway owns database schema changes. Hibernate schema management is configured to `validate`, rather than automatically altering the schema.

The supplied PostgreSQL image includes the pgvector extension, but vector retrieval is not yet implemented. Embedding tables and indexes should be introduced in a later migration after choosing an embedding model and its dimensions.

### Existing database warning

The existing local database is `ai-analyze`, with the application database user `docanalyzer`. Its applied Flyway history includes versions 1 and 2.

**Do not reset the database or edit previously applied migration files to resolve a migration conflict.**

If the current migration files do not match the SQL already recorded in `flyway_schema_history`, Flyway may report a checksum mismatch or migration conflict.

Preserve the migration history and applied migration contents. Reconcile the existing schema and history before adding new changes, and use new higher-numbered migrations for subsequent schema changes.

Do not delete and recreate document or chunk rows merely to investigate retrieval duplication. First determine where duplicate content is introduced, then implement a safe, targeted correction.

## Testing

Run the test suite:

```bash
mvn test
```

The intended test coverage includes filename sanitization, chunk splitting, retrieval abstention, grounded source context, and unauthenticated API access.

Treat test results as unknown until the suite has actually been executed. Add integration tests with PostgreSQL Testcontainers for upload, persistence, ownership isolation, retrieval, and storage-failure cleanup.

Recommended additional regression tests:

- Repeated text in an uploaded document
- Chunk index stability and boundary behavior
- DOCX extraction consistency
- Duplicate chunk detection
- Authorization across different document owners
- Model provider configuration and unavailable model handling
- Empty or unreadable documents
- Maximum upload and extracted-text limits
- UI form validation and unauthorized browser requests

## Known limitations

| Area | Current state |
|---|---|
| Text extraction | Apache Tika |
| OCR | Not implemented |
| Chunking | Bounded, overlapping text chunks |
| Retrieval | Lexical token overlap |
| Embeddings | Not implemented |
| Vector search | Not implemented |
| Hybrid search/reranking | Not implemented |
| LLM | Configurable Ollama or OpenAI-compatible provider |
| Citations | Source excerpts; no formal entailment validation |
| Kafka | Dependency/configuration only; no ingestion worker |
| Redis | Dependency/configuration only; no application rate limiting |
| UI | Minimal Thymeleaf and Bootstrap baseline |
| Identity | Configured in-memory user |
| Tenant administration | Not implemented |
| Production observability and quotas | Incomplete |

## Next milestones

1. Add PostgreSQL-backed integration tests and storage-failure cleanup.
2. Investigate and prevent duplicate content in extraction and chunking.
3. Complete UI validation, document selection, and answer/source rendering against the implemented controllers.
4. Move ingestion to Kafka workers and add OCR for scanned files.
5. Add embeddings, pgvector storage, hybrid retrieval, and reranking.
6. Improve scope detection, citation-entailment validation, and adversarial evaluation.
7. Add rate limits, quotas, observability, retention/deletion propagation, malware scanning, and deployment hardening.

## Configuration file

The primary Spring configuration is:

`src/main/resources/application.properties`

The YAML configuration file has been removed. Keep environment-specific secrets outside the repository and document any new configuration keys when they are added to the application.
