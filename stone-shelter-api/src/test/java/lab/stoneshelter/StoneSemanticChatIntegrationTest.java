package lab.stoneshelter;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.enums.StoneSemanticSearchMode;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneChatConversationEntityToStoneChatHistoryResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatTurnEntityToStoneChatMessageResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper;
import lab.stoneshelter.mappers.entities.StoneChatMessageRequestToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchDecisionToStoneExactSearchCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchResultEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import lab.stoneshelter.repositories.StoneExactSearchRepository;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.services.OllamaSemanticEvidenceProvider;
import lab.stoneshelter.services.OllamaSemanticSearchProvider;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.services.StoneChatAdmissionService;
import lab.stoneshelter.services.StoneChatAvailabilityService;
import lab.stoneshelter.services.StoneChatContextSelector;
import lab.stoneshelter.services.StoneChatConversationSerializationService;
import lab.stoneshelter.services.StoneChatbotService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneExactSearchProvider;
import lab.stoneshelter.services.StoneExactSearchService;
import lab.stoneshelter.services.StoneExactSearchSettings;
import lab.stoneshelter.services.StoneExactSearchValidationService;
import lab.stoneshelter.services.StoneSemanticCandidateService;
import lab.stoneshelter.services.StoneSemanticEvidenceProvider;
import lab.stoneshelter.services.StoneSemanticEvidenceService;
import lab.stoneshelter.services.StoneSemanticEvidenceValidationService;
import lab.stoneshelter.services.StoneSemanticSearchProvider;
import lab.stoneshelter.services.StoneSemanticSearchService;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import lab.stoneshelter.services.StoneSemanticSearchValidationService;
import lab.stoneshelter.services.StoneStructuredModelService;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = StoneChatbotControllerTest.ChatConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneSemanticChatIntegrationTest extends StoneChatRedisTestSupport {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff").asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Clock clock = Clock.systemUTC();
    private final List<Long> ids = new ArrayList<>();
    private final StoneSemanticSearchProvider interpretation = mock(StoneSemanticSearchProvider.class);
    private final StoneDocumentEmbeddingService embedding = mock(StoneDocumentEmbeddingService.class);
    private final StoneExactSearchProvider oldProvider = mock(StoneExactSearchProvider.class);
    private StoneSemanticSearchRepository repository;
    private StoneSemanticCandidateService candidates;
    private StoneSemanticSearchService search;
    private StoneSemanticEvidenceProvider evidence;
    @Autowired RestTestClient restClient;
    @Autowired StoneChatbotService httpChatbot;

    @BeforeAll static void database() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase(); liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(StoneSemanticRetrievalTest.Transactions.class, StoneExactSearchRepository.class, StoneSemanticSearchRepository.class);
        context.refresh(); jdbcTemplate = context.getBean(JdbcTemplate.class);
    }
    @AfterAll static void closeDatabase() { if (context != null) context.close(); DATABASE.stop(); }
    @AfterEach void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbcTemplate.update("DELETE FROM stone_document_index WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        for (long id : ids) { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id=?", id); jdbcTemplate.update("DELETE FROM stone WHERE id=?", id); }
    }
    @BeforeEach void prepareServices() {
        repository = spy(new StoneSemanticSearchRepository(jdbcTemplate));
        var settings = new StoneSemanticSearchSettings(new MockEnvironment(), new StoneCardSearchSettings(new MockEnvironment()));
        var guard = mock(StoneStructuredModelService.class);
        when(guard.checkingOutputTokens()).thenReturn(512);
        // Real packing with its guarded capacity API; no inference in this ticket.
        doAnswer(call -> true).when(guard).fits(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        evidence = (request, repair) -> {
            List<Map<String, Object>> judgements = new ArrayList<>();
            for (int index = 0; index < request.sources().size(); index++) for (var wish : request.wishes()) {
                String text = request.sources().get(index).text(); String verdict = "UNKNOWN", quote = null;
                if (wish.key().equals("use")) {
                    if (text.contains("must not be placed on a shelf")) { verdict = "CONTRADICTED"; quote = "must not be placed on a shelf"; }
                    else if (text.contains("suitable for a shelf")) { verdict = "SUPPORTED"; quote = "suitable for a shelf"; }
                } else if (wish.key().equals("color") && wish.text().equals("red") && text.contains("red")) { verdict = "SUPPORTED"; quote = "red"; }
                Map<String, Object> row = new java.util.LinkedHashMap<>(); row.put("key", wish.key()); row.put("sourceId", "s" + index);
                row.put("verdict", verdict); row.put("quote", quote); judgements.add(row);
            }
            return new StoneModelResponseEntity(JSON.writeValueAsString(Map.of("judgements", judgements)), 20, 20);
        };
        var checks = new StoneSemanticEvidenceService(evidence, new StoneSemanticEvidenceValidationService(),
                new StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper(), new OllamaSemanticEvidenceProvider(guard), settings, clock,
                new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper());
        when(embedding.embed(any())).thenAnswer(call -> {
            StoneDocumentEmbeddingCriteria criteria = call.getArgument(0);
            assertThat(criteria.admissionMode()).isEqualTo(lab.stoneshelter.enums.ModelAdmissionMode.CUSTOMER);
            assertThat(criteria.query()).isTrue(); assertThat(criteria.deadline()).isBeforeOrEqualTo(clock.instant().plusSeconds(50));
            return new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), vector(1), 3);
        });
        candidates = new StoneSemanticCandidateService(repository, checks, embedding, new StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper(), settings, clock);
        var exact = new StoneExactSearchService(oldProvider, new StoneExactSearchValidationService(), context.getBean(StoneExactSearchRepository.class),
                new StoneExactSearchResultEntityToStoneChatTurnEntityMapper(), new StoneExactSearchDecisionToStoneExactSearchCriteriaMapper(), clock);
        search = new StoneSemanticSearchService(interpretation, new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()), exact, candidates,
                new StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper(), new StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper(),
                new StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper(), clock);
    }

    @Test void mixedSearchRanksCardSupportBeforeHigherTypeScoresAndKeepsBoundedOverflowAndPartials() {
        for (int index = 0; index < 8; index++) card("wrong", "GRANITE", "LARGE", "RESERVED", "suitable for a shelf", 1.0);
        long typeOnly = card("Type only", "BASALT", "SMALL", "AVAILABLE", null, null);
        long cardFirst = card("Card first", "BASALT", "SMALL", "AVAILABLE", "suitable for a shelf", 0.6);
        long veto = card("Veto", "BASALT", "SMALL", "AVAILABLE", "red; must not be placed on a shelf", 1.0);
        for (int index = 0; index < 5; index++) card("Card " + index, "BASALT", "SMALL", "AVAILABLE", "suitable for a shelf", 0.7);
        document("BASALT", "Basalt is suitable for a shelf.");
        output(decision("[\"BASALT\"]", "[\"SMALL\"]", "red", "shelf", false));
        StoneChatTurnEntity turn = search.complete(turn("Find small red basalt for a shelf"), List.of(), clock.instant().plusSeconds(120));
        assertThat(turn.getStones()).hasSize(5).noneMatch(stone -> stone.id() == veto || stone.id() == typeOnly || stone.id() == cardFirst);
        assertThat(turn.getText()).contains("7", "More candidates", "unconfirmed wishes: red").doesNotContain("encyclopedia", "source", "Which");
        assertThat(turn.getSemanticCandidates()).allSatisfy(candidate -> assertThat(candidate.unmatched()).containsExactly(new StoneSemanticWish("color", "red")));
        verify(embedding).embed(any()); verify(interpretation).invoke(any(), any(), any(), any()); verifyNoInteractions(oldProvider);
        var prepared = candidates.prepare(criteria("red", "shelf", true), execution());
        assertThat(prepared.getCandidates()).extracting(candidate -> candidate.getStone().getId()).endsWith(cardFirst, typeOnly);
        assertThat(prepared.getFoundCount()).isEqualTo(7); assertThat(prepared.getHasMore()).isTrue();
    }

    @Test void zeroSemanticOnlyDoesNotFallBackAndMixedAlternativesAreBoundedAndVetoed() {
        long old = card("Old", "BASALT", "SMALL", "AVAILABLE", null, 0.1);
        long fresh = card("Fresh", "BASALT", "SMALL", "AVAILABLE", null, 0.1);
        long veto = card("Veto", "BASALT", "SMALL", "AVAILABLE", "must not be placed on a shelf", 0.1);
        jdbcTemplate.update("UPDATE stone SET admission_date=? WHERE id=?", Timestamp.from(Instant.parse("2026-10-10T00:00:00Z")), old);
        var result = candidates.prepare(criteria(null, "shelf", true), execution());
        assertThat(result.getMode()).isEqualTo(StoneSemanticSearchMode.EXACT_FALLBACK);
        assertThat(result.getCandidates()).extracting(candidate -> candidate.getStone().getId()).containsExactly(fresh, old).doesNotContain(veto);
        assertThat(result.getText()).contains("exact alternatives", "unconfirmed wishes");
        clearInvocations(repository, embedding);
        var empty = candidates.prepare(criteria(null, "shelf", false), execution());
        assertThat(empty.getCandidates()).isEmpty(); assertThat(empty.getMode()).isEqualTo(StoneSemanticSearchMode.NO_MATCH);
        verify(repository, never()).searchAlternatives(any()); verify(repository).search(any()); verify(embedding).embed(any());
    }

    @Test void partialLateRemovalRefillsFromTheWholePoolAndNeverRefreshes() {
        List<Long> eligible = new ArrayList<>();
        for (int index = 0; index < 7; index++) eligible.add(card("Shelf " + index, "BASALT", "SMALL", "AVAILABLE", "suitable for a shelf", 1.0));
        doAnswer(call -> {
            jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", eligible.get(0));
            jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id=?", eligible.get(1));
            jdbcTemplate.update("DELETE FROM stone WHERE id=?", eligible.get(1));
            return call.callRealMethod();
        }).when(repository).findEligible(any(), any());
        var execution = execution(); var result = candidates.prepare(criteria(null, "shelf", false), execution);
        assertThat(result.getCandidates()).extracting(candidate -> candidate.getStone().getId()).containsExactlyElementsOf(eligible.subList(2, 7));
        assertThat(result.getFoundCount()).isEqualTo(5); assertThat(result.getHasMore()).isFalse(); assertThat(execution.getRefreshSpent()).isFalse();
        verify(repository).search(any()); verify(embedding).embed(any());
    }

    @Test void wholePoolDisappearanceRefreshesOnceWithSameVectorAndStopsAfterSecondDisappearance() {
        List<Long> original = new ArrayList<>();
        for (int index = 0; index < 6; index++) original.add(card("First " + index, "BASALT", "SMALL", "AVAILABLE", "suitable for a shelf", 1.0));
        AtomicInteger rechecks = new AtomicInteger();
        doAnswer(call -> {
            List<Long> found = call.getArgument(0);
            for (long id : found) jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", id);
            if (rechecks.getAndIncrement() == 0) card("Replacement", "BASALT", "SMALL", "AVAILABLE", "suitable for a shelf", 1.0);
            return call.callRealMethod();
        }).when(repository).findEligible(any(), any());
        var execution = execution(); var result = candidates.prepare(criteria(null, "shelf", false), execution);
        assertThat(result.getCandidates()).isEmpty(); assertThat(execution.getRefreshSpent()).isTrue(); assertThat(execution.getQueryEmbeddings()).isEqualTo(1);
        verify(repository, times(2)).search(any()); verify(embedding).embed(any());
    }

    @Test void semanticRejectionDoesNotRefreshAndExactOnlyNeverEmbedsOrCallsOldInterpreter() {
        card("Unknown", "BASALT", "SMALL", "AVAILABLE", null, 1.0);
        var execution = execution(); var zero = candidates.prepare(criteria(null, "shelf", false), execution);
        assertThat(zero.getCandidates()).isEmpty(); assertThat(execution.getRefreshSpent()).isFalse(); verify(repository).search(any());
        clearInvocations(embedding, repository);
        output(decision("[\"BASALT\"]", "[]", null, null, false));
        var exact = search.complete(turn("Find basalt"), List.of(), clock.instant().plusSeconds(120));
        assertThat(exact.getStones()).hasSize(1); assertThat(exact.getText()).contains("matching available stones");
        assertThat(exact.getSemanticSearchState().getWishes()).isEmpty(); verifyNoInteractions(embedding, repository, oldProvider);
        output(decision("[]", "[]", null, null, true));
        assertThat(search.complete(turn("new search: I want a stone"), List.of(exact), clock.instant().plusSeconds(120)).getText()).contains("Which stone type", "What size");
    }

    @Test void completedMetadataSurvivesRedisReplayAndFollowupsReplaceWishesAndResetBothGroups() {
        card("Shelf", "BASALT", "SMALL", "AVAILABLE", "suitable for a shelf", 1.0);
        StoneChatSessionRepository sessions = new StoneChatSessionRepository(redis, settings);
        String session = sessions.findOrCreate(null).id(); UUID conversation = UUID.randomUUID();
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), StoneChatSessionRepository.ownerKey(conversation),
                StoneChatActiveTurnRepository.key(session, conversation), StoneChatConversationRepository.key(session, conversation)));
        sessions.authorize(session, conversation, true);
        var history = new StoneChatConversationRepository(redis, settings);
        var active = new StoneChatActiveTurnRepository(redis, settings);
        var serialization = new StoneChatConversationSerializationService(active, settings);
        var availability = new StoneChatAvailabilityService(sessions, history, new StoneChatRateLimiterRepository(redis, settings), active, redis,
                serialization, new StoneChatContextSelector());
        availability.configureModels(oldProvider, mock(lab.stoneshelter.services.ModelInvocationAdmissionService.class)); availability.recover();
        var admission = mock(StoneChatAdmissionService.class);
        var chatbot = new StoneChatbotService(history, serialization, admission, availability, new StoneChatMessageRequestToStoneChatTurnEntityMapper(),
                new StoneChatTurnEntityToStoneChatMessageResponseMapper(), new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector());
        var delegate = mock(lab.stoneshelter.services.StoneRagConversationService.class);
        when(delegate.complete(any(), any(), any())).thenAnswer(call -> new lab.stoneshelter.entities.StoneRagPreparedTurnEntity(
                search.complete(call.getArgument(0), call.getArgument(1), call.getArgument(2)), null));
        chatbot.configureRagSearch(delegate, clock, settings);
        var request = new StoneChatMessageRequest(conversation, UUID.randomUUID(), "red basalt for a shelf", null);
        output(decision("[\"BASALT\"]", "[]", "red", "shelf", false));
        try {
            var answer = chatbot.sendMessage(request, session, () -> {});
            var saved = history.findById(session, conversation).turns();
            assertThat(saved).hasSize(1); assertThat(saved.getFirst().getSemanticCandidates()).hasSize(1);
            assertThat(redis.get(StoneChatConversationRepository.key(session, conversation))).doesNotContain("embedding", "modelDeadline", "queryCache");
            clearInvocations(interpretation, embedding);
            assertThat(chatbot.sendMessage(request, session, () -> { throw new AssertionError("Replay renewed cookie"); })).isEqualTo(answer);
            verifyNoInteractions(interpretation, embedding);
            output(decision("[]", "[\"SMALL\"]", "blue", null, false));
            var followup = search.complete(turn("make it small and blue"), saved, clock.instant().plusSeconds(120));
            assertThat(followup.getSemanticSearchState().getWishes()).containsExactly(new StoneSemanticWish("color", "blue"), new StoneSemanticWish("use", "shelf"));
            assertThat(followup.getExactSearchCriteria().getStoneTypes()).containsExactly(lab.stoneshelter.enums.StoneType.BASALT);
            output(decision("[]", "[\"SMALL\"]", null, null, true));
            var reset = search.complete(turn("New search: small stones"), List.of(followup), clock.instant().plusSeconds(120));
            assertThat(reset.getSemanticSearchState().getWishes()).isEmpty(); assertThat(reset.getExactSearchCriteria().getStoneTypes()).isEmpty();
            doThrow(new StoneSemanticSearchLimitExceededException()).when(interpretation).invoke(any(), any(), any(), any());
            assertThatThrownBy(() -> chatbot.sendMessage(new StoneChatMessageRequest(conversation, UUID.randomUUID(), "shelf", null), session, () -> {}))
                    .isInstanceOf(StoneSemanticSearchLimitExceededException.class);
            assertThat(chatbot.findById(conversation, session).messages()).hasSize(2);
            doThrow(new IllegalStateException("Provider down")).when(interpretation).invoke(any(), any(), any(), any());
            assertThatThrownBy(() -> chatbot.sendMessage(new StoneChatMessageRequest(conversation, UUID.randomUUID(), "shelf", null), session, () -> {}))
                    .isInstanceOf(StoneChatUnavailableException.class);
            assertThatThrownBy(() -> chatbot.findById(conversation, session)).isInstanceOf(StoneChatUnavailableException.class);
            assertThat(history.findById(session, conversation).turns()).hasSize(1);
        } finally { serialization.close(); }
    }

    @Test void interpretationIsStrictRepairsOnceAndRequiredStateNeverSilentlyDisappears() {
        output("{broken");
        assertThatThrownBy(() -> search.complete(turn("shelf"), List.of(), clock.instant().plusSeconds(120)))
                .isInstanceOf(InvalidStoneSemanticSearchOutputException.class);
        verify(interpretation, times(2)).invoke(any(), any(), any(), any()); verifyNoInteractions(embedding);
        var validator = new StoneSemanticSearchValidationService(new StoneExactSearchValidationService());
        String good = decision("[]", "[]", null, "not for a shelf", false);
        assertThat(validator.validate(good).getWishes().use()).isEqualTo("not for a shelf");
        assertThatThrownBy(() -> validator.validate(good + " {} ")).isInstanceOf(InvalidStoneSemanticSearchOutputException.class);
        assertThatThrownBy(() -> validator.validate(good.replace("\"color\":null", "\"color\":\"null\""))).isInstanceOf(InvalidStoneSemanticSearchOutputException.class);
        var transport = mock(StoneStructuredModelService.class); when(transport.checkingOutputTokens()).thenReturn(512);
        when(transport.fits(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        var provider = new OllamaSemanticSearchProvider(transport, clock, new StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper());
        assertThatThrownBy(() -> provider.invoke(turn("shelf"), List.of(), clock.instant().plusSeconds(120), null)).isInstanceOf(StoneSemanticSearchLimitExceededException.class);
        verify(transport, never()).invoke(anyString(), anyString(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test void generatedJsonAndYamlKeepPublicFieldsAndExposeSemanticLimitAndInvalidOutput() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml")) {
            String body = restClient.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
            OpenAPI api = path.endsWith("yaml") ? Yaml31.mapper().readValue(body, OpenAPI.class) : Json31.mapper().readValue(body, OpenAPI.class);
            assertThat(api.getPaths().get("/api/v1/chat/messages").getPost().getResponses().get("503").getDescription()).contains("CHAT_SEMANTIC_LIMIT_EXCEEDED");
            assertThat(api.getComponents().getSchemas().get("StoneChatMessageResponse").getProperties().keySet()).containsExactlyInAnyOrder("text", "stones");
            assertThat(api.getComponents().getSchemas().get("StoneChatHistoryMessage").getProperties().keySet()).containsExactlyInAnyOrder("role", "text", "stones", "context", "turnId");
        }
        doThrow(new StoneSemanticSearchLimitExceededException()).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173")
                .body(new StoneChatMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "shelf", null)).exchange().expectStatus().isEqualTo(503)
                .expectHeader().doesNotExist("Retry-After").expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.code").isEqualTo("CHAT_SEMANTIC_LIMIT_EXCEEDED");
        doThrow(new InvalidStoneSemanticSearchOutputException()).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173")
                .body(new StoneChatMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "shelf", null)).exchange().expectStatus().isEqualTo(502)
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_INVALID_MODEL_OUTPUT");
        doThrow(new lab.stoneshelter.exceptions.ModelCapacityExceededException()).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173")
                .body(new StoneChatMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "shelf", null)).exchange().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "1").expectHeader().doesNotExist("Set-Cookie")
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_CAPACITY_EXCEEDED");
        doThrow(new lab.stoneshelter.exceptions.ModelTokenBudgetExceededException(java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC))).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173")
                .body(new StoneChatMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "shelf", null)).exchange().expectStatus().isEqualTo(429)
                .expectHeader().value("Retry-After", value -> assertThat(Long.parseLong(value)).isBetween(1L, 86400L))
                .expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.code").isEqualTo("CHAT_TOKEN_BUDGET_EXCEEDED");
        doThrow(new StoneChatUnavailableException()).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173")
                .body(new StoneChatMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "shelf", null)).exchange().expectStatus().isEqualTo(503)
                .expectHeader().doesNotExist("Retry-After").expectHeader().doesNotExist("Set-Cookie")
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_UNAVAILABLE");
    }

    private long card(String name, String type, String size, String status, String biography, Double score) {
        long id = jdbcTemplate.queryForObject("INSERT INTO stone(storage_uuid,name,stone_type,size,adoption_status,admission_date,biography) VALUES(?,?,?,?,?,?,?) RETURNING id",
                Long.class, UUID.randomUUID(), name, type, size, status, Timestamp.from(Instant.parse("2026-10-13T00:00:00Z")), biography); ids.add(id);
        if (score != null) jdbcTemplate.update("INSERT INTO stone_card_embedding(stone_id,text,embedding_profile,embedding) VALUES(?,?,?,?::vector)",
                id, "Stale projection", (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), Arrays.toString(vector(score)));
        return id;
    }
    private void document(String type, String text) {
        jdbcTemplate.update("INSERT INTO stone_document_index(document_key,source_version,embedding_profile,language,indexed_fragments) VALUES(?,?,?,'en',1)",
                StoneDocumentIndexEntity.DOCUMENT_KEY, "a".repeat(64), (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)));
        jdbcTemplate.update("INSERT INTO stone_document_fragment(document_key,fragment_order,source_version,embedding_profile,language,stone_type,section_path,passage_text,embedding) VALUES(?,0,?,?,'en',?,'test',?,?::vector)",
                StoneDocumentIndexEntity.DOCUMENT_KEY, "a".repeat(64), (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), type, text, Arrays.toString(vector(1)));
    }
    private double[] vector(double score) { double[] vector = new double[1024]; vector[0] = score; vector[1] = Math.sqrt(1 - score * score); return vector; }
    private StoneSemanticSearchCriteria criteria(String color, String use, boolean exact) {
        StoneExactSearchCriteria criteria = new StoneExactSearchCriteria();
        if (exact) { criteria.setStoneTypes(List.of(lab.stoneshelter.enums.StoneType.BASALT)); criteria.setStoneSizes(List.of(lab.stoneshelter.enums.StoneSize.SMALL)); }
        List<StoneSemanticWish> wishes = new ArrayList<>(); if (color != null) wishes.add(new StoneSemanticWish("color", color)); if (use != null) wishes.add(new StoneSemanticWish("use", use));
        return new StoneSemanticSearchCriteria(criteria, wishes);
    }
    private StoneSemanticSearchExecutionEntity execution() {
        var execution = new StoneSemanticSearchExecutionEntity(); execution.setDeadline(clock.instant().plusSeconds(120)); execution.setModelDeadline(execution.getDeadline()); return execution;
    }
    private StoneChatTurnEntity turn(String message) { var turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage(message); return turn; }
    private void output(String output) { doReturn(new StoneModelResponseEntity(output, 20, 20)).when(interpretation).invoke(any(), any(), any(), any()); }
    private String decision(String types, String sizes, String color, String use, boolean reset) {
        return "{\"action\":\"SEARCH\",\"criteria\":{\"stoneTypes\":" + types + ",\"stoneSizes\":" + sizes
                + ",\"availability\":[],\"admissionDateRanges\":[]},\"resetSearch\":" + reset
                + ",\"reference\":null,\"questions\":[],\"reason\":\"\",\"wishes\":{\"color\":" + JSON.writeValueAsString(color)
                + ",\"use\":" + JSON.writeValueAsString(use) + ",\"other\":[]}}";
    }
}
