# ADR 0001: Stack versions

Date: 2026-10-03

Status: Accepted; project runtime changed to JDK 23.0.2 by explicit user instruction.

## Context

The constitution requires concrete stack versions to be recorded here. The
[bootstrap specification](../../specs/0001-project-bootstrap/spec.md) currently
requires JDK 23.0.2 and Spring Boot 4.1.1. The user selected JDK 23.0.2 instead
of Java 25. This decision records stable
upstream releases, excluding milestones, release candidates, snapshots, nightly
builds, and betas. It does not authorize implementing AI features.

## Decision

Use these project versions. JDK 23.0.2 is an intentional selection rather than
the newest GA Java release; the other releases were verified on 2026-10-03:

| Component | Selected version | Official source |
| --- | --- | --- |
| Java / JDK | 23.0.2 | [Spring Boot Java requirements](https://docs.spring.io/spring-boot/system-requirements.html) |
| Spring Boot | 4.1.1 | [Spring Boot reference and stable releases](https://docs.spring.io/spring-boot/) |
| Spring AI | 2.0.1 | [Spring AI reference and stable releases](https://docs.spring.io/spring-ai/reference/) |
| PostgreSQL | 18.6 | [PostgreSQL latest releases](https://www.postgresql.org/) |
| Liquibase Community | 5.0.4 | [Liquibase releases](https://github.com/liquibase/liquibase/releases) |
| Apache Commons Lang | 3.20.0 | [Official release download](https://commons.apache.org/proper/commons-lang/download_lang.cgi) |
| Testcontainers for Java | 2.0.5 | [Testcontainers Java releases](https://github.com/testcontainers/testcontainers-java/releases) |

Spring AI 2.0.1 belongs to the 2.0.x line, which explicitly supports Spring Boot
4.0.x and 4.1.x according to the [Spring AI getting-started
documentation](https://docs.spring.io/spring-ai/reference/getting-started.html).
Therefore Spring AI 2.0.1 and Spring Boot 4.1.1 satisfy the documented
compatibility requirement. No Boot downgrade is necessary.

Spring Boot 4.1.1 documents compatibility with Java 17 through 26 in its [system
requirements](https://docs.spring.io/spring-boot/system-requirements.html).
Java 23 is within this range. Maven compiles with release 23; Maven Enforcer
3.5.0 requires the actual build JDK to be exactly 23.0.2. The specification is updated before the Maven
configuration. Documented support is distinct from a successful project build.

T0001-002 supporting build pins: Maven 3.9.9, Maven Wrapper 3.3.4 (official
only-script distribution), PostgreSQL JDBC 42.7.13 and JUnit 6.0.3 (the
[Boot 4.1.1 managed coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)),
and ArchUnit 1.5.1 with its JUnit 6 integration (the [official installation
guide](https://www.archunit.org/userguide/html/000_Index.html)). Liquibase 5.0.4
is explicitly pinned over Boot's managed 5.0.3 to follow this ADR. Maven 3.9.9
matches the locally installed Maven version. Spring AI is not added in T0001-002
because the bootstrap specification contains no AI integration requirement.

The bootstrap HTTP test uses the test-scoped `spring-boot-resttestclient` module
4.1.1 with `@AutoConfigureRestTestClient`, following the [Boot running-server
test guidance](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html#testing.spring-boot-applications.with-running-server).
The general test starter does not supply this auto-configuration module.

PostgreSQL integration tests share the singleton container in
`IntegrationTest` within one test JVM, using the [official singleton
lifecycle pattern](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers).
JUnit does not manage its lifecycle; Ryuk cleans it up on JVM exit. Shared
`@ServiceConnection` supplies JDBC and Liquibase connection details from the
shared PostgreSQL container, using the test-scoped `spring-boot-testcontainers`
module 4.1.1 as required by [Boot's service connection support](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html#testing.testcontainers.service-connections).
The unused JUnit Testcontainers extension dependency is removed. This shared
configuration permits Spring context caching when the remaining
context configuration matches. This does not isolate test data or share a
container between JVM forks. With only one integration class, no performance
gain is claimed.

### Compatibility review after selecting JDK 23.0.2

| Dependency group | Review result |
| --- | --- |
| Boot Web MVC, Actuator, Data JPA, Liquibase starter, test starter and Maven plugin 4.1.1 | Same Boot release throughout the project; Java 23 is supported and bootstrap integration passes on JDK 23.0.2. |
| Spring Framework 7.0.9, Spring Data JPA 4.1.1, Hibernate 7.4.5.Final, Tomcat 11.0.24, Jackson 3.1.5 | Resolved through Boot's dependency management; no independent overrides introduced. |
| PostgreSQL JDBC 42.7.13 | Matches Boot's managed version; connects to PostgreSQL 18.6 in the bootstrap test. |
| Liquibase Community 5.0.4 | Explicit patch override of Boot's managed 5.0.3; processes the empty root changelog and creates its tracking table with PostgreSQL 18.6. |
| Testcontainers 2.0.5, including PostgreSQL and JUnit Jupiter modules | Starts and cleans up the PostgreSQL 18.6 test container on JDK 23.0.2. |
| JUnit Jupiter and Platform 6.0.3 | Managed by Boot; [requires Java 17 or newer](https://docs.junit.org/6.0.3/overview.html). |
| ArchUnit JUnit 6 integration 1.5.1 | The [1.5 release line](https://github.com/TNG/ArchUnit/releases) supports JUnit 6; three architecture checks pass on JDK 23.0.2. |
| Spring AI 2.0.1 | Documented Boot 4.1.x compatibility; absent from the project's dependency tree, so no integration was tested. |

Verification used the installed Homebrew OpenJDK 23.0.2, Maven Wrapper 3.3.4
and Maven 3.9.9. `./mvnw clean verify` passed for the original three tests;
after adding the bootstrap integration test, `./mvnw verify` passed with four
tests and zero failures/errors/skips. The integration test checks successful
context startup with PostgreSQL 18.6 and Liquibase enabled, JPA initialization and HTTP
200 with UP status at `/actuator/health`. Maven validation on JDK 25 also
confirmed that Enforcer rejects the wrong runtime version.

The [Liquibase Community 5.0.4 PostgreSQL support
page](https://docs.liquibase.com/community/integration-guide-5-0-4/what-support-does-liquibase-have-for-postgresql)
lists vendor-verified PostgreSQL versions only through 17, not 18. The local
bootstrap test now demonstrates this project's empty root changelog works with
18.6; it does not establish support for every future migration or Liquibase command.

## Consequences

- Future dependency and container configuration must use concrete versions and
  agree with the approved project selections recorded here.
- JDK 23.0.2 replaces Java 25 in the project pins. The remaining bootstrap task
  checklist is unchanged by this compatibility review.
- Spring AI can be selected at 2.0.1 with Boot 4.1.1 when a feature specification
  actually calls for it; recording its version does not add a dependency.
- Compilation, architecture tests and PostgreSQL bootstrap integration pass on
  JDK 23.0.2. Future schema changes still require their own integration tests.
- Release inventory is a dated snapshot and requires deliberate review when
  upgrading. PostgreSQL 19 beta and Spring preview releases are excluded.

## Alternatives

- Downgrade Spring Boot for Spring AI compatibility: unnecessary because the
  stable Spring AI 2.0.x line supports Boot 4.1.x. Any future downgrade requires
  explicit user approval.
- Retain Java 27: rejected by the user; it exceeds Boot's documented range.
- Retain Java 25: superseded by the user's JDK 23.0.2 selection.
- Use prereleases or floating `latest` versions: rejected because they do not
  satisfy the stable-release request or the repository's explicit-version rule.
- Use Boot-managed dependency versions automatically: may simplify integration,
  but must not be presented as the latest upstream releases without verification.

### Apache Commons Lang addition (2026-10-05)

By explicit user choice, add org.apache.commons:commons-lang3:3.20.0 as a
direct dependency for StringUtils. isEmpty checks null and empty strings without
changing whitespace. The photo normalization use case is superseded: photo
values are now preserved unchanged and the service no longer uses StringUtils.
The release requires Java 8 or newer and is compatible with the selected JDK.

### Backend OpenAPI documentation (T0004-001, 2026-10-05)

Pin `org.springdoc:springdoc-openapi-starter-webmvc-ui` to `3.1.1`. The
[official FAQ](https://springdoc.org/faq.html) identifies springdoc 3.x as
compatible with Spring Boot 4 and lists 3.1.1 as the current stable release.
The [official release history](https://github.com/springdoc/springdoc-openapi/releases)
records the 3.1.0 line's upgrade to Spring Boot 4.1.0; 3.1.1 is its stable
patch successor. Keep the existing Spring Boot 4.1.1 and JDK 23.0.2 pins.
This dependency provides generated OpenAPI JSON/YAML and Swagger UI without
a handwritten contract or a separate documentation model.

## Feature 0006 Photo Storage Pins

Feature 0006 uses MinIO server `RELEASE.2025-04-22T22-12-26Z` and
`io.minio:minio:8.5.17`. Official released tags were verified on 2026-10-06:
[server release](https://github.com/minio/minio/releases/tag/RELEASE.2025-04-22T22-12-26Z),
[Java SDK release](https://github.com/minio/minio-java/releases/tag/8.5.17).
The SDK provides object upload/removal and presigned URLs, absent from existing
dependencies. These are explicit reproducibility pins, not a claim of newest
versions; existing backend/runtime pins remain unchanged.

The official Docker Hub image could not be pulled (404) and Quay returned 401
during runtime verification. Build `stone-shelter-minio:RELEASE.2025-04-22T22-12-26Z`
locally from official GitHub release assets, using `alpine:3.22.6` as the base
([official image inventory](https://github.com/docker-library/official-images/blob/master/library/alpine)).
Verify SHA-256 before installing the binary:

| Target | Official release asset | SHA-256 |
| --- | --- | --- |
| amd64 | minio.linux-amd64.RELEASE.2025-04-22T22-12-26Z | 53e2a2cb16c5366ea6fbbc479c19ddb4c6a0948273e752f740fb1fbf27bb817c |
| arm64 | minio.linux-arm64.RELEASE.2025-04-22T22-12-26Z | 6c2f3142c94240206123177f4ba1e360daa5d1e0a4962e90757ef4f92c3ab57c |

These hashes come from the official release asset metadata; both Compose and
Testcontainers use this build definition. The server version remains unchanged.

Feature 0006 also pins `com.twelvemonkeys.imageio:imageio-webp:3.12.0` to
validate uploaded WebP pixel data through Java ImageIO. The JDK does not include
that decoder; structural headers alone accepted a corrupt payload during review.
[Official release](https://github.com/haraldk/TwelveMonkeys/releases/tag/twelvemonkeys-3.12.0)
and [supported formats](https://github.com/haraldk/TwelveMonkeys) were verified
before adding the dependency. It does not add image editing functionality.

## Feature 0014 Infrastructure Pins (2026-10-09)

The user authorized all tasks in
[0014-llm-search-infrastructure](../../specs/0014-llm-search-infrastructure/spec.md).
Preserve JDK 23.0.2, Boot 4.1.1, PostgreSQL 18.6 and Liquibase 5.0.4.

| Component | Selection | Reason / official reference |
| --- | --- | --- |
| PostgreSQL / pgvector image | `pgvector/pgvector:0.8.6-pg18-trixie@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff` | Ready-made PostgreSQL 18.6 / pgvector 0.8.6; [publisher metadata](https://hub.docker.com/r/pgvector/pgvector/tags?name=0.8.6-pg18-trixie); immutable multi-platform digest |
| Redis image | `redis:8.2.10-alpine3.22` | Explicit patch and compact Alpine variant from the [official inventory](https://hub.docker.com/_/redis) |
| Kafka image | `apache/kafka:4.2.2` | Supported patch near Boot's managed client generation; [Apache inventory](https://kafka.apache.org/community/downloads/) |
| Spring AI BOM / Ollama starter | 2.0.1 / 2.0.1 | Existing approved AI pin; Maven Central artifacts and configuration metadata verified |
| Boot Redis / Kafka starters | 4.1.1 / 4.1.1 | Match the Boot parent; connection support absent from current dependencies |
| Ollama host CLI / model | 0.40.2 / `qwen2.5:3b` | User-managed host installation and explicit model choice; no Ollama container or automatic model download |

Manifest inspection confirms pgvector's amd64/arm64 platforms and index digest.
The current host is x86_64. Keep `postgres-data:/var/lib/postgresql`; enable
`vector` using a new Liquibase changeset, without vector/analytics tables.
Readiness and migration checks remain distinct from model/RAG functionality.

Boot 4.1.1 manages Spring Kafka 4.1.1, kafka-clients 4.2.1 and Lettuce
7.5.2.RELEASE. Preserve those transitive versions. Add explicit direct starter
versions and AI BOM 2.0.1, without redundant database or WebFlux dependencies.
The Spring AI 2.0.1 jar confirms the base-url, chat model and never-pull
configuration properties. Disable embeddings; Redis memory TTL is configuration
only, not an implemented ChatMemory adapter.

The Trixie variant preserves the existing PostgreSQL volume's glibc 2.41
collation provider. A read-only check rejected Bookworm (glibc 2.36) before
application migrations; no collation metadata/indexes or catalog data were
changed. Testcontainers uses the same index digest without a tag because its
image-name parser rejects combined tag-plus-digest syntax.

## Feature 0016 Request Protection Pins (2026-10-10)

Pin `com.bucket4j:bucket4j_jdk17-core`, `bucket4j_jdk17-redis-common` and
`bucket4j_jdk17-lettuce` to 8.21.0, and
`io.github.resilience4j:resilience4j-bulkhead` to 2.4.0. Use the existing
Boot-managed Lettuce 7.5.2.RELEASE connection; no second Redis client or
Resilience4j Spring starter is introduced.

On JDK 23.0.2, StoneChatRateLimiterRepositoryTest passed all three tests with
Redis 8.2.10: concurrent consumers respect capacity, idle retention includes
full refill plus grace, and send/history budgets remain separate.
StoneChatConversationSerializationServiceTest and StoneChatActiveTurnRepositoryTest
passed three tests for conversation exclusion, actual-exit permit lifetime,
independent conversations, safe retirement and owner-checked Redis leases.
These qualify the selected integrations within one backend process; model
invocation capacity and provider cancellation remain future work.
