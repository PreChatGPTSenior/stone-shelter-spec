package lab.stoneshelter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.entities.StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneDocumentEmbeddingTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void documentRequestUsesQualifiedProfileWithoutTruncationOrQueryInstruction() throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> {
                    assertThat(httpExchange.getRequestMethod()).isEqualTo("POST");
                    assertThat(httpExchange.getRequestURI().getPath()).isEqualTo("/api/embed");
                    StoneDocumentHttpTestSupport.respond(httpExchange, 200, validResponse(1024));
                })) {
            StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity = service(environment(stoneDocumentHttpTestSupport))
                    .embed(criteria("Stone Encyclopedia > Basalt > Monthly Care\nClean with water.", false));
            assertThat(stoneDocumentHttpTestSupport.request().path("input").asText())
                    .isEqualTo("Stone Encyclopedia > Basalt > Monthly Care\nClean with water.");
            assertThat(stoneDocumentHttpTestSupport.request().path("model").asText()).isEqualTo("qwen3-embedding:0.6b");
            assertThat(stoneDocumentHttpTestSupport.request().path("dimensions").asInt()).isEqualTo(1024);
            assertThat(stoneDocumentHttpTestSupport.request().path("truncate").asBoolean()).isFalse();
            assertThat(stoneDocumentHttpTestSupport.request().size()).isEqualTo(4);
            assertThat(stoneDocumentEmbeddingEntity.vector()).hasSize(1024);
            assertThat(Arrays.stream(stoneDocumentEmbeddingEntity.vector()).allMatch(Double::isFinite)).isTrue();
            assertThat(stoneDocumentEmbeddingEntity.inputTokens()).isEqualTo(16);
            assertThat(stoneDocumentEmbeddingEntity.profile()).isEqualTo(StoneDocumentEmbeddingInputEntity.PROFILE + "/qualified-digest");
            assertThat(stoneDocumentHttpTestSupport.requests()).isEqualTo(1);
        }
    }

    @Test
    void queryInstructionIsAppliedOnlyToQueriesAndProfilesStayCompatible() throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 200, validResponse(1024)))) {
            assertThat(service(environment(stoneDocumentHttpTestSupport)).embed(criteria("How do I clean basalt?", true)).profile())
                    .isEqualTo(StoneDocumentEmbeddingInputEntity.PROFILE + "/qualified-digest");
            assertThat(stoneDocumentHttpTestSupport.request().path("input").asText())
                    .isEqualTo(StoneDocumentEmbeddingInputEntity.QUERY_INSTRUCTION + "How do I clean basalt?");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1023, 1025})
    void incompatibleDimensionsAreRejected(int dimensions) throws Exception {
        assertRejected(validResponse(dimensions), "incompatible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e999", "\"NaN\"", "null", "\"0.5\""})
    void nonfiniteAndNonnumericCoordinatesAreRejected(String coordinate) throws Exception {
        assertRejected(validResponse(1024).replaceFirst("0.5", coordinate), "non-finite or nonnumeric");
    }

    @Test
    void zeroVectorsCannotEnterCosineRetrieval() throws Exception {
        assertRejected(validResponse(1024).replace("0.5", "0.0"), "zero vector");
    }

    @Test
    void modelAndTokenAccountingMustMatchTheSelectedProfile() throws Exception {
        assertRejected(validResponse(1024).replace("qwen3-embedding:0.6b", "other-model"), "incompatible");
        assertRejected(validResponse(1024).replace("\"prompt_eval_count\":16", "\"prompt_eval_count\":0"), "incompatible");
        assertRejected(validResponse(1024).replace("\"prompt_eval_count\":16", "\"prompt_eval_count\":1.5"), "incompatible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"error\":\"bad response\"}"})
    void malformedProviderResponsesAreRejected(String response) throws Exception {
        assertRejected(response, "embedding");
    }

    @Test
    void providerFailureDoesNotRetryOrExposeItsResponse() throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 503, "{\"error\":\"private-provider-detail\"}"))) {
            assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)).embed(criteria("Basalt", false)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class)
                    .hasMessage("The document embedding provider is unavailable.");
            assertThat(stoneDocumentHttpTestSupport.requests()).isEqualTo(1);
        }
    }

    @Test
    void modelCapacityErrorIsDistinctFromDependencyUnavailability() throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 400,
                        "{\"error\":\"input length exceeds maximum context length\"}"))) {
            assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)).embed(criteria("too long", false)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("model capacity");
            assertThat(stoneDocumentHttpTestSupport.requests()).isEqualTo(1);
        }
    }

    @Test
    void requestTimeoutCancelsWaitingWithoutAnotherProviderCall() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> release.await(10, TimeUnit.SECONDS))) {
            try {
                assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)
                        .withProperty("STONE_DOCUMENT_EMBEDDING_TIMEOUT", "PT0.1S")).embed(criteria("Basalt", false)))
                        .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasMessageContaining("timed out");
                assertThat(stoneDocumentHttpTestSupport.requests()).isEqualTo(1);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void deadlineBoundsBothConnectionAndCompleteRequest() {
        StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper mapper = mapper(
                new MockEnvironment().withProperty("STONE_DOCUMENT_OLLAMA_BASE_URL", "http://localhost:11434/"));
        StoneDocumentEmbeddingInputEntity stoneDocumentEmbeddingInputEntity = mapper.toEntity(
                new StoneDocumentEmbeddingCriteria("Basalt", false, NOW.plusMillis(250)));
        assertThat(stoneDocumentEmbeddingInputEntity.getConnectTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(stoneDocumentEmbeddingInputEntity.getRequestTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(stoneDocumentEmbeddingInputEntity.getEndpoint()).isEqualTo("http://localhost:11434/api/embed");
        assertThat(mapper.toEntity((StoneDocumentEmbeddingCriteria) null)).isNull();
    }

    @Test
    void expiredDeadlineAndUnconfiguredProviderNeverStartRemoteWork() throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 200, validResponse(1024)))) {
            assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)).embed(
                    new StoneDocumentEmbeddingCriteria("Basalt", false, NOW)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasMessageContaining("deadline");
            assertThat(stoneDocumentHttpTestSupport.requests()).isZero();
        }
        assertThatThrownBy(() -> service(new MockEnvironment()).embed(criteria("Basalt", false)))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasMessageContaining("configured");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S", "invalid", "PT0.0001S", "PT999999999S"})
    void invalidWaitingLimitsAreDependencyFailures(String timeout) {
        assertThatThrownBy(() -> new StoneDocumentIndexSettings(new MockEnvironment()
                .withProperty("STONE_DOCUMENT_EMBEDDING_TIMEOUT", timeout)).embeddingTimeout())
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
    }

    @Test
    void conservativeDefaultsAndLightweightEstimatorAreExplicit() {
        StoneDocumentIndexSettings stoneDocumentIndexSettings = new StoneDocumentIndexSettings(new MockEnvironment());
        assertThat(stoneDocumentIndexSettings.connectTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(stoneDocumentIndexSettings.embeddingTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(stoneDocumentIndexSettings.importTimeout()).isEqualTo(Duration.ofHours(2));
        assertThat(service(new MockEnvironment()).estimateTokens("Basalt > Care Ritual\nShared silence." )).isEqualTo(11);
    }

    private void assertRejected(String response, String detail) throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 200, response))) {
            assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)).embed(criteria("Basalt", false)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasMessageContaining(detail);
        }
    }

    private String validResponse(int dimensions) {
        return "{\"model\":\"qwen3-embedding:0.6b\",\"prompt_eval_count\":16,\"embeddings\":[["
                + String.join(",", Collections.nCopies(dimensions, "0.5")) + "]]}";
    }

    private MockEnvironment environment(StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport) {
        return new MockEnvironment().withProperty("STONE_DOCUMENT_OLLAMA_BASE_URL", stoneDocumentHttpTestSupport.baseUrl());
    }

    @Test
    void missingModelNeverStartsEmbedding() throws Exception {
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 200, validResponse(1024)))) {
            stoneDocumentHttpTestSupport.modelMetadata(httpExchange ->
                    StoneDocumentHttpTestSupport.respond(httpExchange, 200, "{\"models\":[]}"));
            assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)).embed(criteria("Basalt", false)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasMessageContaining("not installed");
            assertThat(stoneDocumentHttpTestSupport.requests()).isZero();
        }
    }

    @Test
    void modelReplacementDuringEmbeddingCannotPublishIncompatibleVectors() throws Exception {
        AtomicInteger metadataRequests = new AtomicInteger();
        try (StoneDocumentHttpTestSupport stoneDocumentHttpTestSupport = new StoneDocumentHttpTestSupport(
                httpExchange -> StoneDocumentHttpTestSupport.respond(httpExchange, 200, validResponse(1024)))) {
            stoneDocumentHttpTestSupport.modelMetadata(httpExchange -> StoneDocumentHttpTestSupport.respond(
                    httpExchange, 200, "{\"models\":[{\"name\":\"qwen3-embedding:0.6b\",\"digest\":\"digest-"
                            + metadataRequests.incrementAndGet() + "\"}]}"));
            assertThatThrownBy(() -> service(environment(stoneDocumentHttpTestSupport)).embed(criteria("Basalt", false)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasMessageContaining("changed");
            assertThat(stoneDocumentHttpTestSupport.requests()).isEqualTo(1);
        }
    }

    private StoneDocumentEmbeddingCriteria criteria(String text, boolean query) {
        return new StoneDocumentEmbeddingCriteria(text, query, NOW.plusSeconds(3600));
    }

    private StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper mapper(MockEnvironment mockEnvironment) {
        return new StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper(
                new StoneDocumentIndexSettings(mockEnvironment), CLOCK);
    }

    private StoneDocumentEmbeddingService service(MockEnvironment mockEnvironment) {
        StoneDocumentEmbeddingService service = new StoneDocumentEmbeddingService(new OllamaEmbeddingRepository(), mapper(mockEnvironment));
        ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
        when(ledger.reserve(any(), any(), anyLong(), anyLong())).thenAnswer(call ->
                new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), anyLong())).thenAnswer(call ->
                new ModelTokenReservationEntity(call.getArgument(0), java.time.LocalDate.of(2026, 10, 12), 32768, call.getArgument(1)));
        service.configureAdmission(new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), CLOCK), CLOCK);
        return service;
    }
}
