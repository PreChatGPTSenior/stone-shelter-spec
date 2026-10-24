package lab.stoneshelter;

import lab.stoneshelter.entities.StoneRagCompletionEvidenceEntity;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.stoneshelter.entities.StoneModelInvocationEntity;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.sql.DataSource;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.enums.StoneChatStreamEventType;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.entities.StoneRagPreparedTurnEntity;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.exceptions.StoneModelProviderUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneChatConversationEntityToStoneChatHistoryResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatTurnEntityToStoneChatMessageResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneRagDecisionToStoneSemanticSearchDecisionMapper;
import lab.stoneshelter.mappers.dtos.StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGenerationRequestToStoneRagGroundingRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper;
import lab.stoneshelter.mappers.entities.StoneChatMessageRequestToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchDecisionToStoneExactSearchCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchResultEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneRagDecisionToStoneRagAnswerCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneExactSearchRepository;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.repositories.StoneRagKnowledgeRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.OllamaSemanticEvidenceProvider;
import lab.stoneshelter.services.OllamaRagAnswerProvider;
import lab.stoneshelter.services.OllamaRagGroundingProvider;
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
import lab.stoneshelter.services.StoneRagAnswerProvider;
import lab.stoneshelter.services.StoneRagAnswerService;
import lab.stoneshelter.services.StoneRagAnswerValidationService;
import lab.stoneshelter.services.StoneRagGroundingService;
import lab.stoneshelter.services.StoneRagGroundingProvider;
import lab.stoneshelter.services.StoneRagKnowledgeService;
import lab.stoneshelter.services.StoneChatReferenceService;
import lab.stoneshelter.services.StoneRagConversationService;
import lab.stoneshelter.services.StoneRagDecisionValidationService;
import lab.stoneshelter.services.StoneRagRoutingProvider;
import lab.stoneshelter.services.StoneRagSettings;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = StoneChatbotControllerTest.ChatConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneRagChatIntegrationTest extends StoneChatRedisTestSupport {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff").asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Clock clock = Clock.systemUTC();
    private final List<Long> ids = new ArrayList<>();
    private final AtomicInteger interpretations = new AtomicInteger(), generations = new AtomicInteger();
    private final AtomicReference<String> route = new AtomicReference<>();
    private Consumer<StoneRagGenerationRequest> generationHook = ignored -> {};
    private boolean rejectGrounding;
    private StoneChatbotService chatbot;
    private StoneRagConversationService rag;
    private StoneChatConversationRepository history;
    private StoneChatAvailabilityService availability;
    private StoneChatConversationSerializationService serialization;
    private StoneChatSessionRepository sessions;
    private ModelInvocationAdmissionService admission;
    private StoneRagAnswerProvider generator;
    private String session;
    private UUID conversation;
    private StoneDocumentEmbeddingService embedding;
    @Autowired RestTestClient restClient;
    @Autowired StoneChatbotService httpChatbot;
    @BeforeAll static void database() throws Exception {
        DATABASE.start(); DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        var liquibase = new SpringLiquibase(); liquibase.setDataSource(dataSource); liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> dataSource); context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(StoneSemanticRetrievalTest.Transactions.class, StoneExactSearchRepository.class, StoneSemanticSearchRepository.class,
                StoneRagKnowledgeRepository.class, ModelTokenBudgetRepository.class, ModelTokenBudgetTransactionService.class); context.refresh(); jdbcTemplate = context.getBean(JdbcTemplate.class);
    }
    @AfterAll static void close() { if (context != null) context.close(); DATABASE.stop(); }
    @AfterEach void clean() {
        if (serialization != null) serialization.close();
        jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbcTemplate.update("DELETE FROM stone_document_index WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        for (long id : ids) { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id=?", id); jdbcTemplate.update("DELETE FROM stone WHERE id=?", id); }
        // Controlled test accounting only, in this class's isolated database.
        jdbcTemplate.update("DELETE FROM model_token_reservation"); jdbcTemplate.update("DELETE FROM model_token_budget_day");
    }
    @BeforeEach void prepare() {
        sessions = new StoneChatSessionRepository(redis, settings); session = sessions.findOrCreate(null).id(); conversation = UUID.randomUUID(); sessions.authorize(session, conversation, true);
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), StoneChatSessionRepository.ownerKey(conversation),
                StoneChatActiveTurnRepository.key(session, conversation), StoneChatConversationRepository.key(session, conversation)));
        history = new StoneChatConversationRepository(redis, settings);
        var active = new StoneChatActiveTurnRepository(redis, settings); serialization = new StoneChatConversationSerializationService(active, settings);
        availability = new StoneChatAvailabilityService(sessions, history, new StoneChatRateLimiterRepository(redis, settings), active, redis, serialization, new StoneChatContextSelector());
        admission = new ModelInvocationAdmissionService(context.getBean(ModelTokenBudgetTransactionService.class), new ModelInvocationSettings(new MockEnvironment()), clock);
        availability.configureModels(mock(StoneExactSearchProvider.class), admission); availability.configureRagControls(context.getBean(StoneRagKnowledgeRepository.class)); availability.recover();
        generator = (request, repair) -> { generations.incrementAndGet(); generationHook.accept(request); return response(draft(request)); };
        configurePipeline();
    }
    private void configurePipeline() {
        var semanticSettings = new StoneSemanticSearchSettings(new MockEnvironment(), new StoneCardSearchSettings(new MockEnvironment()));
        var modelSettings = new StoneExactSearchSettings(new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://qualification.invalid"));
        var ragSettings = new StoneRagSettings(new MockEnvironment(), modelSettings);
        var guard = new StoneStructuredModelService(mock(OllamaChatRepository.class), admission, modelSettings, clock);
        var evidence = new StoneSemanticEvidenceService((request, repair) -> {
            var root = jsonMapper.createObjectNode(); var rows = root.putArray("judgements");
            for (int index = 0; index < request.sources().size(); index++) for (var wish : request.wishes()) {
                var source = request.sources().get(index); boolean supported = wish.key().equals("use") && source.text().contains("suitable for a shelf");
                var row = rows.addObject().put("key", wish.key()).put("sourceId", "s" + index).put("verdict", supported ? "SUPPORTED" : "UNKNOWN");
                if (supported) row.put("quote", "suitable for a shelf"); else row.putNull("quote");
            }
            return response(root.toString());
        }, new StoneSemanticEvidenceValidationService(), new StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper(), new OllamaSemanticEvidenceProvider(guard), semanticSettings, clock,
                new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper());
        embedding = mock(StoneDocumentEmbeddingService.class); when(embedding.embed(any())).thenReturn(query());
        var candidates = new StoneSemanticCandidateService(context.getBean(StoneSemanticSearchRepository.class), evidence, embedding,
                new StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper(), semanticSettings, clock);
        var exact = new StoneExactSearchService(mock(StoneExactSearchProvider.class), new StoneExactSearchValidationService(), context.getBean(StoneExactSearchRepository.class),
                new StoneExactSearchResultEntityToStoneChatTurnEntityMapper(), new StoneExactSearchDecisionToStoneExactSearchCriteriaMapper(), clock);
        var semantic = new StoneSemanticSearchService(mock(StoneSemanticSearchProvider.class), new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()), exact, candidates,
                new StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper(), new StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper(), new StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper(), clock);
        var knowledge = new StoneRagKnowledgeService(context.getBean(StoneRagKnowledgeRepository.class), context.getBean(StoneSemanticSearchRepository.class), embedding,
                new StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper(), semanticSettings, clock);
        StoneRagGroundingProvider checker = (request, repair) -> response("{\"valid\":" + !rejectGrounding + ",\"reason\":\"Checked\"}");
        var grounding = new StoneRagGroundingService(checker, new OllamaRagGroundingProvider(guard), new StoneRagGenerationRequestToStoneRagGroundingRequestMapper(),
                new StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper(), semanticSettings, clock);
        var answers = new StoneRagAnswerService(generator, new StoneRagAnswerValidationService(), grounding,
                new StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper(new StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper()), ragSettings, clock);
        var selectionMapper = new StoneRagDecisionToStoneSemanticSearchDecisionMapper();
        StoneRagRoutingProvider routing = (turn, previous, deadline, repair) -> { interpretations.incrementAndGet(); return response(route.get()); };
        rag = new StoneRagConversationService(routing, new StoneRagDecisionValidationService(new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()), selectionMapper),
                selectionMapper, semantic, candidates, exact, new StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper(), knowledge, context.getBean(StoneRagKnowledgeRepository.class), new StoneChatReferenceService(),
                new StoneRagDecisionToStoneRagAnswerCriteriaMapper(), new StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper(), new StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper(), answers, availability, clock, ragSettings);
        var customer = mock(StoneChatAdmissionService.class); doAnswer(call -> { availability.assertReady(); sessions.authorize(call.getArgument(0), call.getArgument(1), call.getArgument(2)); return null; }).when(customer).authorize(anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        chatbot = new StoneChatbotService(history, serialization, customer, availability, new StoneChatMessageRequestToStoneChatTurnEntityMapper(),
                new StoneChatTurnEntityToStoneChatMessageResponseMapper(), new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector()); chatbot.configureRagSearch(rag, clock, settings);
    }
    @Test void mixedSelectionExplainsEachIdOncePreservesReplayAndNoCandidatesNeverGenerate() {
        long first = card("Alpha"), second = card("Beta"); route.set(selection("[\"BASALT\"]", "[\"SMALL\"]", "shelf", "basalt care"));
        var request = request("Find small basalt for a shelf and explain care"); var answer = chatbot.sendMessage(request, session, () -> {});
        assertThat(answer.stones()).extracting(stone -> stone.id()).containsExactly(first, second); assertThat(answer.text()).contains("Alpha:", "Beta:", "shelf").doesNotContain("source", "document");
        assertThat(interpretations.get()).isEqualTo(1); assertThat(generations.get()).isEqualTo(1); assertThat(history.findById(session, conversation).turns()).hasSize(1);
        assertThat(chatbot.sendMessage(request, session, () -> { throw new AssertionError("Replay renewed"); })).isEqualTo(answer); assertThat(generations.get()).isEqualTo(1);
        for (long id : ids) jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", id);
        var absent = chatbot.sendMessage(request("same conditions"), session, () -> {}); assertThat(absent.stones()).isEmpty(); assertThat(generations.get()).isEqualTo(1);
    }
    @Test void knowledgePreservesStateAndConvenienceLinkNeverReplacesSelectionOrdinals() {
        long first = card("Alpha"), second = card("Beta"); route.set(selection("[\"BASALT\"]", "[\"SMALL\"]", "shelf", ""));
        chatbot.sendMessage(request("Find small basalt for a shelf"), session, () -> {});
        route.set(knowledge("{\"kind\":\"ORDINAL\",\"ordinal\":1}"));
        var explained = chatbot.sendMessage(request("How to care for the first stone?"), session, () -> {}); assertThat(explained.stones()).extracting(stone -> stone.id()).containsExactly(first);
        var saved = history.findById(session, conversation).turns(); assertThat(saved.getLast().getRagState().mode()).isEqualTo(StoneRagMode.KNOWLEDGE);
        assertThat(saved.getLast().getSemanticSearchState().getWishes()).isEqualTo(saved.getFirst().getSemanticSearchState().getWishes());
        route.set(knowledge("{\"kind\":\"ORDINAL\",\"ordinal\":2}"));
        assertThat(chatbot.sendMessage(request("What about the second stone?"), session, () -> {}).stones()).extracting(stone -> stone.id()).containsExactly(second);
        jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", second);
        int before = generations.get(); assertThat(chatbot.sendMessage(request("Explain second again"), session, () -> {}).stones()).isEmpty(); assertThat(generations.get()).isEqualTo(before);
    }
    @Test void knowledgeFailureIncludingConvenienceLinkDoesNotCommitWhileSelectionUsesCleanTemplate() {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", "")); rejectGrounding = true;
        var answer = chatbot.sendMessage(request("shelf basalt"), session, () -> {}); assertThat(answer.text()).doesNotContain("fits the shelf");
        assertThat(history.findById(session, conversation).turns().getFirst().getRagState().completion()).isEqualTo(StoneRagCompletionKind.TEMPLATE);
        route.set(knowledge("{\"kind\":\"ORDINAL\",\"ordinal\":1}"));
        assertThatThrownBy(() -> chatbot.sendMessage(request("explain first"), session, () -> {})).isInstanceOf(InvalidStoneRagOutputException.class);
        assertThat(history.findById(session, conversation).turns()).hasSize(1); availability.assertReady();
    }
    @Test void partialLossRemovesStaleExplanationAndDoesNotRefillFromUnshownCandidates() {
        List<Long> selected = new ArrayList<>(); for (int index = 0; index < 7; index++) selected.add(card("Stone " + index));
        route.set(selection("[\"BASALT\"]", "[]", "shelf", "")); generationHook = request -> jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", selected.getFirst());
        var answer = chatbot.sendMessage(request("shelf basalt"), session, () -> {});
        assertThat(answer.stones()).extracting(stone -> stone.id()).containsExactlyElementsOf(selected.subList(1, 5));
        assertThat(answer.text()).doesNotContain("Stone 0:").contains("6", "More candidates"); assertThat(generations.get()).isEqualTo(1);
    }
    @Test void totalGeneratedLossUsesOnlyOneSharedRefreshAndOneQueryVector() {
        card("First"); route.set(selection("[\"BASALT\"]", "[]", "shelf", ""));
        generationHook = request -> { for (var candidate : request.getCandidates()) jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", candidate.getId()); if (generations.get() == 1) card("Replacement"); };
        var answer = chatbot.sendMessage(request("shelf basalt"), session, () -> {}); assertThat(answer.stones()).isEmpty(); assertThat(generations.get()).isEqualTo(2); verify(embedding).embed(any());
    }
    @Test void generationOnlyProviderOutageCommitsOneTemplateButKeepsSendHistoryAndReplayClosed() {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", ""));
        installProviderFailure(false); var request = request("shelf basalt"); var answer = chatbot.sendMessage(request, session, () -> {});
        assertThat(answer.stones()).hasSize(1); assertThat(history.findById(session, conversation).turns()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT actual_tokens FROM model_token_reservation", Long.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT charged_tokens FROM model_token_budget_day", Long.class)).isEqualTo(8192);
        assertThatThrownBy(() -> chatbot.sendMessage(request, session, () -> {})).isInstanceOf(StoneChatUnavailableException.class);
        assertThatThrownBy(() -> chatbot.findById(conversation, session)).isInstanceOf(StoneChatUnavailableException.class);
        availability.recover(); assertThat(chatbot.sendMessage(request, session, () -> { throw new AssertionError("Replay renewed"); })).isEqualTo(answer);
    }
    @Test void aControlFailureCannotUseTheGenerationOnlyExceptionAndNoPairIsSaved() {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", "")); installProviderFailure(true);
        assertThatThrownBy(() -> chatbot.sendMessage(request("shelf basalt"), session, () -> {})).isInstanceOf(StoneChatUnavailableException.class);
        assertThat(history.findById(session, conversation).turns()).isEmpty();
    }
    @Test void exactMixedCareKeepsNoSemanticWishAndRecountsAfterPartialLossWithoutRefill() {
        for (int index = 0; index < 7; index++) card("Exact " + index);
        route.set(selection("[\"BASALT\"]", "[\"SMALL\"]", null, "basalt care"));
        generationHook = request -> jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", request.getCandidates().getFirst().getId());
        var answer = chatbot.sendMessage(request("Find small basalt and tell me how to care for it"), session, () -> {});
        assertThat(answer.stones()).hasSize(4); assertThat(answer.text()).contains("Found 6 matching");
        assertThat(history.findById(session, conversation).turns().getFirst().getSemanticSearchState().getWishes()).isEmpty();
        assertThat(generations.get()).isEqualTo(1); verify(embedding).embed(any());
    }
    @Test void expiredTemplateProofAndLedgerProbeFailureCannotCommit() {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", "")); installProviderFailure(false);
        StoneChatTurnEntity turn = new StoneChatMessageRequestToStoneChatTurnEntityMapper().toEntity(request("shelf basalt"));
        var prepared = rag.complete(turn, List.of(), clock.instant().plusSeconds(120));
        var evidence = prepared.completionEvidence();
        var expired = new StoneRagPreparedTurnEntity(prepared.turn(), new StoneRagCompletionEvidenceEntity(
                clock.instant().minusSeconds(1), evidence.controlEpoch(), evidence.selection(), evidence.failure()));
        assertThatThrownBy(() -> availability.assertCompletion(expired, session, conversation)).isInstanceOf(StoneChatUnavailableException.class);
        var failedLedger = mock(ModelInvocationAdmissionService.class); doThrow(new IllegalStateException("ledger unavailable")).when(failedLedger).probe();
        availability.configureModels(mock(StoneExactSearchProvider.class), failedLedger);
        assertThatThrownBy(() -> availability.assertCompletion(prepared, session, conversation)).isInstanceOf(IllegalStateException.class);
        assertThat(history.findById(session, conversation).turns()).isEmpty();
    }
    @Test void lostCommitAcknowledgementKeepsOneRecoverablePairAndNeverReportsAFalseSuccess() {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", ""));
        var controlledRedis = spy(redis);
        var loseAck = new AtomicBoolean(true);
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (call.<String>getArgument(0).contains("local previous =") && call.<List<String>>getArgument(1).contains(StoneChatConversationRepository.key(session, conversation))
                    && loseAck.compareAndSet(true, false)) throw controlledRedis.unavailable(new IllegalStateException("Commit ack lost"));
            return result;
        }).when(controlledRedis).eval(anyString(), any(), any(String[].class));
        serialization.close(); redis = controlledRedis; prepare();
        var request = request("shelf basalt");
        assertThatThrownBy(() -> chatbot.sendMessage(request, session, () -> { throw new AssertionError("Unconfirmed commit renewed cookie"); })).isInstanceOf(StoneChatUnavailableException.class);
        assertThat(history.findById(session, conversation).turns()).hasSize(1);
        availability.recover();
        assertThat(chatbot.sendMessage(request, session, () -> { throw new AssertionError("Replay renewed cookie"); }).stones()).hasSize(1);
        assertThat(history.findById(session, conversation).turns()).hasSize(1); assertThat(generations.get()).isEqualTo(1);
    }
    private void installProviderFailure(boolean controlFailure) {
        var modelSettings = new StoneExactSearchSettings(new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://qualification.invalid"));
        var repository = new OllamaChatRepository() {
            @Override public void probe(String endpoint) {}
            @Override public StoneModelResponseEntity invoke(StoneModelInvocationEntity invocation) {
                if (controlFailure) redis.unavailable(new IllegalStateException("controlled Redis fence failure"));
                throw new IllegalStateException("controlled provider unavailable");
            }
        };
        generator = new OllamaRagAnswerProvider(new StoneStructuredModelService(repository, admission, modelSettings, clock), new StoneRagSettings(new MockEnvironment(), modelSettings)); configurePipeline();
    }
    @Test void nativeContractsKeepPublicShapesAndDocumentRagLimitsAndInvalidKnowledge() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml")) {
            String body = restClient.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
            OpenAPI api = path.endsWith("yaml") ? Yaml31.mapper().readValue(body, OpenAPI.class) : Json31.mapper().readValue(body, OpenAPI.class);
            assertThat(api.getPaths().get("/api/v1/chat/messages").getPost().getResponses().get("503").getDescription()).contains("CHAT_RAG_LIMIT_EXCEEDED");
            assertThat(api.getComponents().getSchemas().get("StoneChatMessageResponse").getProperties().keySet()).containsExactlyInAnyOrder("text", "stones");
            assertThat(api.getComponents().getSchemas().get("StoneChatHistoryMessage").getProperties().keySet()).containsExactlyInAnyOrder("role", "text", "stones", "context", "turnId");
        }
        doThrow(new StoneRagAnswerLimitExceededException()).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(request("knowledge")).exchange().expectStatus().isEqualTo(503)
                .expectHeader().doesNotExist("Retry-After").expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.code").isEqualTo("CHAT_RAG_LIMIT_EXCEEDED");
        doThrow(new InvalidStoneRagOutputException()).when(httpChatbot).sendMessage(any(), anyString(), any());
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(request("knowledge")).exchange().expectStatus().isEqualTo(502)
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_INVALID_MODEL_OUTPUT");
    }
    @Test void streamingUsesTheCheckedRagPipelineAndOnlyConfirmedHistoryCanSupplyDeltas() throws Exception {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", ""));
        var request = request("shelf basalt"); var stream = chatbot.startMessage(request, session, () -> () -> {});
        assertThat(stream.nextEvent(3).getType()).isEqualTo(StoneChatStreamEventType.STARTED);
        var saved = stream.getCompletion().get(5, TimeUnit.SECONDS);
        assertThat(history.findById(session, conversation).turns()).hasSize(1);
        StringBuilder text = new StringBuilder(); boolean completed = false;
        for (var event = stream.nextEvent(3); event != null; event = stream.nextEvent(3)) {
            if (event.getType() == StoneChatStreamEventType.TEXT_DELTA) { assertThat(event.getStones()).isEmpty(); text.append(event.getText()); }
            if (event.getType() == StoneChatStreamEventType.COMPLETED) { completed = true; assertThat(event.getText()).isEqualTo(saved.text()); assertThat(event.getStones()).isEqualTo(saved.stones()); }
        }
        assertThat(completed).isTrue(); assertThat(text.toString()).isEqualTo(saved.text()); assertThat(generations.get()).isEqualTo(1);
        assertThat(chatbot.sendMessage(request, session, () -> { throw new AssertionError("Replay cookie renewal"); }).text()).isEqualTo(saved.text());
        assertThat(generations.get()).isEqualTo(1); stream.transportExited();
    }
    @Test void streamingKeepsTheCurrentTurnTemplateExceptionWithoutReopeningLaterOperations() throws Exception {
        card("Alpha"); route.set(selection("[\"BASALT\"]", "[]", "shelf", "")); installProviderFailure(false);
        var stream = chatbot.startMessage(request("shelf basalt"), session, () -> () -> {});
        var saved = stream.getCompletion().get(5, TimeUnit.SECONDS);
        assertThat(saved.text()).contains("Alpha").doesNotContain("It fits the shelf."); assertThat(saved.stones()).hasSize(1);
        assertThat(history.findById(session, conversation).turns()).hasSize(1);
        assertThatThrownBy(availability::assertReady).isInstanceOf(StoneChatUnavailableException.class); stream.transportExited();
    }
    private StoneChatMessageRequest request(String text) { return new StoneChatMessageRequest(conversation, UUID.randomUUID(), text, null); }
    private long card(String name) { long id = jdbcTemplate.queryForObject("INSERT INTO stone(storage_uuid,name,stone_type,size,adoption_status,admission_date,biography) VALUES(?,?,'BASALT','SMALL','AVAILABLE',?,'suitable for a shelf') RETURNING id", Long.class, UUID.randomUUID(), name, Timestamp.from(Instant.now())); ids.add(id); jdbcTemplate.update("INSERT INTO stone_card_embedding(stone_id,text,embedding_profile,embedding) VALUES(?,?,?,?::vector)", id, name, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), Arrays.toString(query().vector())); return id; }
    private StoneDocumentEmbeddingEntity query() { double[] vector = new double[1024]; vector[0] = 1; return new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), vector, 1); }
    private StoneModelResponseEntity response(String text) { return new StoneModelResponseEntity(text, 20, 20); }
    private String selection(String types, String sizes, String use, String topic) { return "{\"mode\":\"SELECTION\",\"criteria\":{\"stoneTypes\":" + types + ",\"stoneSizes\":" + sizes + ",\"availability\":[],\"admissionDateRanges\":[]},\"resetSearch\":false,\"reference\":null,\"questions\":[],\"reason\":\"\",\"wishes\":{\"color\":null,\"use\":" + jsonMapper.writeValueAsString(use) + ",\"other\":[]},\"topic\":" + jsonMapper.writeValueAsString(topic) + "}"; }
    private String knowledge(String reference) { return "{\"mode\":\"KNOWLEDGE\",\"criteria\":{\"stoneTypes\":[\"BASALT\"],\"stoneSizes\":[],\"availability\":[],\"admissionDateRanges\":[]},\"resetSearch\":false,\"reference\":" + reference + ",\"questions\":[],\"reason\":\"\",\"wishes\":{\"color\":null,\"use\":null,\"other\":[]},\"topic\":\"basalt care\"}"; }
    private String draft(StoneRagGenerationRequest request) {
        var root = jsonMapper.createObjectNode().put("introduction", "Options."); var stones = root.putArray("stones");
        if (request.getMode() == StoneRagMode.KNOWLEDGE) root.put("knowledge", "Basalt care: keep dry.");
        else { for (var candidate : request.getCandidates()) stones.addObject().put("id", candidate.getId()).put("explanation",
                    "It fits the shelf." + (candidate.getUnmatched().isEmpty() ? "" : " The requested color is unconfirmed.")); root.putNull("knowledge"); }
        return root.toString();
    }
}
