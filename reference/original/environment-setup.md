# Local Environment Setup: Ollama

This guide sets up host Ollama from scratch for Stone Shelter. Follow the
macOS steps below for the project's current development machine. Ollama runs
on the host; the repository's Docker Compose stack uses that server.

Project versions are recorded in [ADR-0001](docs/adr/0001-stack-versions.md).
The Markdown indexing task is [T0017-002](specs/0017-markdown-document-indexing/tasks.md).

## 1. Install Ollama

Use the project's pinned **Ollama 0.40.2** release, rather than a floating
installer version. If Ollama is already installed, check it before reinstalling:

```sh
ollama --version
```

On macOS:

1. Download [Ollama.dmg for 0.40.2](https://github.com/ollama/ollama/releases/download/v0.40.2/Ollama.dmg)
   from the [official release](https://github.com/ollama/ollama/releases/tag/v0.40.2).
2. Open the disk image and drag Ollama into Applications.
3. Open Ollama from Applications. Accept its prompt to make the `ollama` command
   available in the terminal, then open a new terminal window.
4. Run `ollama --version` again and check that the version is 0.40.2.

The [macOS requirements](https://docs.ollama.com/macos) are macOS 14 or newer;
Apple Silicon supports CPU/GPU execution and Intel Macs use CPU execution.
Allow disk space for the runtime, both model downloads and working memory.

For another operating system, use the same
[0.40.2 release](https://github.com/ollama/ollama/releases/tag/v0.40.2):
`OllamaSetup.exe` on Windows, or the matching Linux architecture archive.
Follow the official [Windows](https://docs.ollama.com/windows) or
[Linux](https://docs.ollama.com/linux) installation instructions, keeping the
project's version pin.

## 2. Start and Check the Local Server

The macOS app starts the server. Check it from a terminal:

```sh
curl --fail --connect-timeout 30 --max-time 30 http://localhost:11434/api/version
ollama list
```

The first command should return version JSON. An empty model list is normal
before downloading models.

If the app is not running and you prefer a terminal-managed server, run this
in a separate terminal and keep that terminal open:

```sh
ollama serve
```

Use one server on port 11434: either the app or `ollama serve`. An
`address already in use` message usually means a server is already running.
These commands follow the [official CLI reference](https://docs.ollama.com/cli).

## 3. Download the Project Models

The project uses two separate models:

| Model | Purpose | Approximate download size |
| --- | --- | --- |
| [qwen2.5:3b](https://ollama.com/library/qwen2.5:3b) | Selected chat model | 1.9 GB |
| [qwen3-embedding:0.6b](https://ollama.com/library/qwen3-embedding:0.6b) | Document/query vectors for Markdown indexing | 639 MB |

Download them explicitly while the server is running:

```sh
ollama pull qwen2.5:3b
ollama pull qwen3-embedding:0.6b
```

If `qwen2.5:3b` is already present, only the second download is needed to
resolve the missing embedding model. Check both installations:

```sh
ollama list
ollama show qwen2.5:3b
ollama show qwen3-embedding:0.6b
```

The list should contain both exact model names. Installing the chat model
alone does not provide the embedding model. Model downloads are an explicit
operator step; the backend does not download them automatically.

## 4. Configure the Backend Connection

### Backend or Tests Running Directly on the Host

Set these variables in the terminal used to start the backend or Maven:

```sh
export SPRING_AI_OLLAMA_BASE_URL=http://localhost:11434
export SPRING_AI_OLLAMA_CHAT_MODEL=qwen2.5:3b
export SPRING_AI_OLLAMA_INIT_PULL_MODEL_STRATEGY=never
export STONE_DOCUMENT_OLLAMA_BASE_URL=http://localhost:11434
```

`SPRING_AI_OLLAMA_BASE_URL` configures the existing Spring AI connection;
`STONE_DOCUMENT_OLLAMA_BASE_URL` configures the document embedding adapter.
Set both for a backend that uses both integrations.

Exports apply to that terminal and its child processes. For an IntelliJ run
configuration, add the variables to its Environment variables field.
An existing IDE process does not inherit later terminal exports.

### Backend Running in Docker Desktop

Use the host address from inside the backend container:

```dotenv
SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434
SPRING_AI_OLLAMA_CHAT_MODEL=qwen2.5:3b
SPRING_AI_OLLAMA_INIT_PULL_MODEL_STRATEGY=never
STONE_DOCUMENT_OLLAMA_BASE_URL=http://host.docker.internal:11434
```

For first-time repository setup, copy `.env.example` to `.env` only if `.env`
does not already exist. For an existing setup, edit the relevant entries in
place and preserve other settings. Compose reads `.env` for interpolation;
a value reaches the container only if the service passes it through.
Check the container environment when using the new document adapter.
Host Maven runs do not automatically load the repository's `.env` file.

If Docker cannot reach host Ollama, configure a Docker-accessible listen
address. For the macOS app:

```sh
launchctl setenv OLLAMA_HOST "0.0.0.0:11434"
```

Quit and reopen Ollama after changing this setting. For a terminal-managed
server, quit the app first and start:

```sh
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

This binds to all network interfaces; use it when Docker access is needed.
The [official configuration guide](https://docs.ollama.com/faq) explains
`OLLAMA_HOST` and platform-specific environment settings. Inside a container,
`localhost` refers to that container. The browser uses the backend API and
does not need direct Ollama access.

## 5. Check Embedding Output

From the host, send one document input using the selected model and dimensions.
The following smoke check requires Python 3 and prints a short result instead
of the entire vector:

```sh
curl --fail --connect-timeout 30 --max-time 1830 \
  http://localhost:11434/api/embed \
  -H 'Content-Type: application/json' \
  -d '{"model":"qwen3-embedding:0.6b","input":"Stone Encyclopedia > Basalt > Monthly Care\nRemove dust with a soft cloth.","dimensions":1024,"truncate":false}' \
  | python3 -c 'import json, math, sys; data = json.load(sys.stdin); vectors = data["embeddings"]; assert len(vectors) == 1; assert len(vectors[0]) == 1024; assert all(math.isfinite(value) for value in vectors[0]); print("model:", data["model"], "dimensions:", len(vectors[0]), "input tokens:", data["prompt_eval_count"])'
```

Expect `model: qwen3-embedding:0.6b dimensions: 1024` and a positive input-token
count. The [embedding API](https://docs.ollama.com/api/embed) documents these
fields and `truncate=false`, which rejects oversized input instead of truncating
it. This smoke check verifies connectivity and vector shape; full functional
qualification remains the project's focused test.

## 6. Run the T0017-002 Qualification Test

Use the pinned JDK 23.0.2. On macOS, select the installed JDK and verify it:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 23)"
"$JAVA_HOME/bin/java" -version
```

The reported version must be 23.0.2. Then, from the repository root:

```sh
export STONE_DOCUMENT_OLLAMA_BASE_URL=http://localhost:11434
cd stone-shelter-api
./mvnw -Dtest=StoneDocumentEmbeddingQualificationTest \
  -Dstone.document.qualification=true test
```

The explicit flag enables the real-model check. It validates the installed
model and representative embeddings/retrieval; it does not download a model
or import a document into the database. Installing the model alone does not
mark T0017-002 complete: record the qualification result against its task.

## Troubleshooting

| Symptom | Check or action |
| --- | --- |
| `ollama: command not found` | Reopen the terminal after installation; on macOS, reopen the app and complete CLI setup. |
| Connection refused on port 11434 | Open the Ollama app or start `ollama serve`, then repeat the version check. |
| HTTP 404 with `model not found` | Run `ollama list` against the same server, then `ollama pull qwen3-embedding:0.6b` for the selected embedding model. |
| HTTP 404 with models already listed | Check the URL and route: embeddings use `/api/embed`; model metadata uses `/api/show`. |
| Pull fails after a connection interruption | Restore connectivity and rerun the same `ollama pull` command; verify installation with `ollama show`. |
| Host checks pass but Docker cannot connect | Check the listen address and use `host.docker.internal` from Docker Desktop. |
| Qualification says the endpoint is unconfigured | Export `STONE_DOCUMENT_OLLAMA_BASE_URL` in the Maven terminal or set it in the IDE run configuration. |
| Maven reports the wrong Java version | Set `JAVA_HOME` to the installed JDK 23.0.2 before running the focused test. |

For project infrastructure beyond Ollama, see [README.md](README.md).
## Run API

On first launch, copy the local environment template from the repository root:

```sh
cp .env.example .env
```

## Usefull command
docker compose up --build
docker compose up -d --build
docker compose down
docker compose ps

Compose loads `.env` automatically. The template contains development-only
database and MinIO credentials; edit `.env` if needed. Existing `.env` files
need the new MinIO settings from `.env.example`. Keep this file out of Git.

## Local AI Infrastructure

The backend, PostgreSQL 18.6 with pgvector 0.8.6, MinIO, Redis and a single
Kafka 4.2.2 KRaft broker run in Compose. Ollama runs on the host and is managed
by the user. Existing chatbot responses remain fixed; infrastructure does not
implement LLM search, RAG, conversation memory, analytics or streaming.

Preserve an existing `.env`; manually add the non-secret settings documented
in `.env.example` when updating an older setup. To use the published local
examples explicitly without loading a private env file, add
`--env-file .env.example` to each Compose command.

```sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
docker compose stop
docker compose start
```

Start only the infrastructure when the backend is not needed:

```sh
docker compose up -d --wait postgres minio redis kafka
```

PostgreSQL and MinIO keep their existing named volumes; Kafka also has a
persistent volume. Normal `stop`, `start` and rebuild commands preserve them.
Do not use `down -v` for routine setup or frontend mode switching. Redis is an
ephemeral local cache with AOF/RDB disabled; its data can disappear on restart.
`CHAT_MEMORY_TTL=PT24H` is a future application setting, not implemented memory.

The backend connects to Redis at `redis:6379`, Kafka at `kafka:9092`, and the
host Ollama server at `http://host.docker.internal:11434`. Kafka's external
listener is `localhost:29092`, overridable by `KAFKA_EXTERNAL_PORT`; controller
traffic and Redis remain internal. Broker logs retain 24 hours by default,
controlled by `KAFKA_LOG_RETENTION_HOURS`. No application topics are created.

```sh
docker compose exec redis redis-cli ping
docker compose exec kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:9092
curl --fail http://localhost:8080/actuator/health
```

Feature scope and evidence live in
[0014-llm-search-infrastructure](specs/0014-llm-search-infrastructure/spec.md).

## Frontend Startup Modes

For the complete Docker stack, including the frontend:

```sh
docker compose --profile frontend up -d --build --wait --wait-timeout 180
```

Open http://localhost:5173/stone-shelter/catalog. `FRONTEND_PORT` changes the
published port. The frontend profile uses pinned Node/npm and the existing
Vite API-mode server, proxying `/api` and `/images` to the Docker backend.
SPA page URLs support direct navigation/reload. This is local development
infrastructure, not a production static-web deployment.

For chat sends, preserve the existing root `.env` and configure the exact browser
origin there; older local files may lack the setting from `.env.example`:

```dotenv
CHAT_ALLOWED_ORIGINS=http://localhost:5173
STONE_DOCUMENT_OLLAMA_BASE_URL=http://host.docker.internal:11434
```

If using another frontend origin, list the exact approved origins separated by
commas. An empty setting rejects every send with `CHAT_ORIGIN_REJECTED`, even
when catalog requests and application health succeed. After changing `.env`,
recreate only the backend to apply the container environment, preserving data:

```sh
docker compose up -d --no-deps --no-build --force-recreate --wait --wait-timeout 180 stone-shelter-api
```

Keep Ollama reachable from the backend before recovery. If the widget already
shows an unavailable state, use its **Restore history** action before retrying;
the recovery GET verifies chat dependencies, while a send does not reopen failed
readiness automatically. This operation does not require a database reset.

The widget requires `/api/v1/chat/messages/stream` in the generated backend
contract. A reused older image can pass health checks while lacking that route;
rebuild the current already implemented backend before recreating its container
when this happens. Semantic retrieval also needs the explicit embedding endpoint
above passed through Compose; the Spring AI chat endpoint alone does not configure it.

Keep the UI image current too: older widgets may omit mandatory `turnId` and
receive HTTP 400 `Invalid request` from the current backend for every send.
Rebuild the current UI and recreate only its container, then reload the browser
page to receive the current JavaScript:

```sh
docker compose --profile frontend up -d --build --no-deps --wait --wait-timeout 120 stone-shelter-ui
```

For fast host UI edits, stop only the frontend container if it is running:

```sh
docker compose --profile frontend stop stone-shelter-ui
docker compose up -d --build --wait --wait-timeout 180
cd stone-shelter-ui
npm ci
# First launch only; preserve an existing .env.local.
cp .env.example .env.local
npm run dev:api
```

Use Node 24.21.0 and npm 11.19.0. Host Vite proxies to `http://localhost:8080`
through `API_PROXY_TARGET`; keep `VITE_API_BASE_URL` empty. Source edits receive
Vite hot updates without rebuilding Docker or restarting the backend. To choose
a different host port, use `npm run dev:api -- --port 5174 --strictPort`.

To switch back, stop the host Vite process and enable the Compose frontend
profile again. Switching frontend modes does not stop infrastructure or remove
its volumes. Existing `npm run dev` and explicit `npm run dev:mock` remain
available; API errors do not switch to mocks.

## User-Managed Host Ollama

The installed Ollama 0.40.4 runtime was explicitly approved for the local chat
repair on 2026-10-14; earlier qualification evidence remains tied to its recorded
versions. Preserve the selected model profiles. The backend uses
`SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434`, not container
localhost. If the server is not running, start it yourself in a host terminal
with a Docker-accessible listen address:

```sh
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

If using the macOS Ollama app instead, configure its host environment and restart
it yourself following the [official host configuration instructions](https://docs.ollama.com/faq).
Use one running server, not both app and terminal instances on the same port.
The browser calls the backend; it does not need direct Ollama access or new CORS
settings. Docker Desktop resolves [host.docker.internal](https://docs.docker.com/desktop/features/networking/networking-how-tos/).

In another host terminal, download only the selected model when ready:

```sh
ollama --version
ollama pull qwen2.5:3b
ollama list
curl --fail http://localhost:11434/api/version
```

The published model is about 1.9 GB, Q4_K_M; allow additional disk space for the
host runtime. The repository never installs Ollama or pulls models at startup.
Host models survive container rebuilds. To remove only this model deliberately:

```sh
ollama rm qwen2.5:3b
```

Check version and model inventory from the backend container without generating
answers or downloading anything:

```sh
docker compose exec stone-shelter-api curl --fail --max-time 5 http://host.docker.internal:11434/api/version
docker compose exec stone-shelter-api curl --fail --max-time 5 http://host.docker.internal:11434/api/tags
```

If host Ollama is unavailable, these checks remain pending; the Docker stack and
existing API still start. Spring AI auto-pulling and embeddings are disabled.
No model capability or business-logic test belongs to infrastructure setup.

## Stone Photo Storage

Compose builds a pinned MinIO release from official binaries with checksum
verification. PostgreSQL stores gallery metadata and MinIO stores image bytes
in persistent volumes. The bucket is private. `MINIO_BROWSER_ENDPOINT` must
be reachable by the browser; the local default is `http://localhost:9000`.
The MinIO console is at `http://localhost:9001`.

Use the generated contract at `/v3/api-docs` or `/v3/api-docs.yaml` for current
HTTP requirements. For an existing stone, upload a supported image with:

```sh
curl --fail-with-body -F 'file=@stone.png;type=image/png' \
  http://localhost:8080/api/v1/stones/1/photos
```

Read `/api/v1/stones/1` for ordered gallery metadata and temporary image URLs.
The first successfully added photo supplies the catalog cover. The React UI uses the real API by default and supports optional photo uploads
when creating a stone. It also offers an explicit standalone mock mode. Run both
modes using the [UI instructions](stone-shelter-ui/README.md).

Photo deletion is deferred to a scheduler inside the backend. By default it runs
at 03:00 UTC daily, checks MinIO write readiness, and processes paced batches.
Deletion requests remove database metadata and queue owned files; failed uploads
retain durable cleanup intent. Storage failures stop a run and preserve work for
the next day. Configure cron/timezone and processing limits using the
PHOTO_CLEANUP_* settings in `.env.example`. The current deployment uses one
backend scheduler instance.

Feature decisions and verification are in
[0006-stone-details](specs/0006-stone-details/spec.md).

## Stone Reservations

POST `/api/v1/stones/{id}/reservations` with JSON fields `applicantName` and
`contactDetails` to reserve an available stone. Both fields accept arbitrary
nonblank text. Creation returns 201 with the reservation id, stoneId, RESERVED
adoptionStatus and createdAt. The reservation and status change commit together;
duplicates or unavailable stones return 409 ProblemDetail. Deleting the stone
removes its dependent reservation. Generated OpenAPI remains the HTTP contract.

The React details page submits adoption applications through the real API and
refreshes details and the AVAILABLE-only catalog. Explicit mock mode and ordinary
UI tests retain the same flow without a backend. Reservation decisions are in
[0007-adopt-stone](specs/0007-adopt-stone/spec.md); integrated launch instructions
and verification are in [0011-ui-api-integration](specs/0011-ui-api-integration/spec.md).

## Stone Chatbot Demo

`POST /api/v1/chat/messages` accepts `conversationId` (UUID), `message` and
optional `context.stoneId`. The controller returns fixed `text` and ordered
`stones` references; no AI or server history is connected. The UI preserves its
conversation across client-side page changes and resets it on reload.
See [feature 0012](specs/0012-stone-chatbot/spec.md).

From `stone-shelter-api`, run only its backend checks with the pinned JDK:

```sh
./mvnw -Dtest=StoneChatbotControllerTest,StoneChatbotArchitectureTest verify
```

To include the actual UI adapter/Vite proxy smoke, first install the UI's pinned
dependencies and run `npm run build` there, then use:

```sh
./mvnw -Dtest=StoneChatbotControllerTest,StoneChatbotArchitectureTest,StoneChatbotTransportTest -Dchatbot.transport=true verify
```

The transport test starts temporary local servers without a database or catalog
mutations. It is opt-in so ordinary backend verification does not require Node
or installed frontend dependencies.

## Verify
    curl localhost:8080/actuator/health
    http://localhost:8080/actuator/health

## Layout
    specs/NNN-*/                      one directory per feature
    docs/adr/                         decisions and rationale
    docs/glossary.md                  domain vocabulary
    stone-shelter-api/                Spring Boot backend
    stone-shelter-ui/                 React frontend

## Semantic Candidate Search Settings

Feature 0025 uses these validated command/process environment settings; no catalog
color field or automatic source import is added:

| Variable | Default | Constraint |
| --- | --- | --- |
| STONE_SEMANTIC_SEARCH_CANDIDATE_LIMIT | 10 | Integer at least 6 |
| STONE_CARD_SEARCH_MIN_SIMILARITY | 0.5 | Existing inclusive finite cosine threshold in [-1, 1] |
| STONE_SEMANTIC_SEARCH_DOCUMENT_LIMIT | 10 | Positive integer |
| STONE_SEMANTIC_SEARCH_DOCUMENT_MIN_SIMILARITY | 0.5 | Inclusive finite threshold in [-1, 1] |
| STONE_SEMANTIC_SEARCH_MAX_EVIDENCE_CALLS | 20 | Positive whole-turn invocation cap including corrections/refresh |

Invalid supplied values fail explicitly. These are initial accepted settings,
not a claim that the thresholds have been broadly qualified or tuned. Query
embeddings reuse the existing compatible profile and CUSTOMER model controls.

## RAG Answer Settings

Feature 0026 reads CHAT_RAG_OUTPUT_TOKENS (default 1024, positive and below the
configured total chat context) and CHAT_RAG_MAX_GENERATION_CALLS (default 4,
integer from 1 through 4). Interpretation/checking keep the existing 512 output
allowance. The final five seconds of the original turn deadline are reserved
for final eligibility/template/history work; model calls never gain extra time.


### Feature 0029 client transport bound

`VITE_CHAT_STREAM_TIMEOUT_MS` defaults to `135000`; use a positive integer no
greater than `2147483647`. It bounds one fetch/read or history request without
resetting on heartbeat. It closes client transport and does not cancel an admitted
backend turn. API mode never changes to mocks or automatically resends via JSON.
The initial 1 MiB unfinished-frame/active-answer bounds fit the checked initial
8192/512/1024 backend configuration; reassess compatibility if those limits change.


## Real-Model Qualification Test Quota

MODEL_DAILY_TOKEN_BUDGET is a positive configurable UTC-day token quota. The
application default is 100000; feature 0027 qualification defaults to 200000,
explicitly doubled for test runs. Supply it in the test command environment:

```sh
MODEL_DAILY_TOKEN_BUDGET=200000 ./mvnw -B '-Dtest=StoneSearchQueryQualificationTest#verifyTokenBudgetConfiguration+verifyQuotaInterruptionClassification' test
```

Run from stone-shelter-api with the approved command-scoped JAVA_HOME/service/
owned-fixture settings from the existing qualification setup. A supplied positive
value overrides the test default and is recorded in new execution evidence.
The temporary owned launcher must preserve this value instead of replacing it
with 100000. Keep actual isolated usage/reservations; changing the quota does not
reset accounting or rewrite older attempts' settings/results. Token exhaustion
marks verification INCOMPLETE_BUDGET_EXHAUSTED and stops collection, including
quota-caused saved templates. Controlled quota checks perform no live inference.
