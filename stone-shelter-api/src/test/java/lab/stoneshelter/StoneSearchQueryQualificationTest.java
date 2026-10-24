package lab.stoneshelter;

import lab.stoneshelter.enums.StoneRagMode;

import lab.stoneshelter.shared.StoneRagCandidateRequest;

import lab.stoneshelter.shared.StoneRagGenerationRequest;

import lab.stoneshelter.entities.StoneRagExecutionEntity;

import lab.stoneshelter.entities.StoneRagAnswerResultEntity;

import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;

import lab.stoneshelter.entities.StoneModelInvocationEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import lab.stoneshelter.criteria.StoneSemanticPassageCriteria;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticRetrievalCriteria;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
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
import lab.stoneshelter.services.StoneSemanticEvidenceService;
import lab.stoneshelter.services.StoneSemanticEvidenceValidationService;
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
import lab.stoneshelter.services.StoneRagSettings;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import tools.jackson.core.type.TypeReference;
import lab.stoneshelter.entities.StoneExactSearchResultEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneEntity;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.lang.reflect.InvocationTargetException;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.services.OllamaSemanticSearchProvider;
import lab.stoneshelter.services.OllamaRagRoutingProvider;
import lab.stoneshelter.services.StoneChatOriginService;
import lab.stoneshelter.services.StoneChatClientAddressService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.mappers.entities.StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper;
import lab.stoneshelter.shared.StoneChatMessageResponse;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fixed original baseline; natural cases use final production model providers and controls. */
class StoneSearchQueryQualificationTest extends StoneChatRedisTestSupport {
    private final Clock realClock = Clock.systemUTC();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<Map<String, Object>> stages = new ArrayList<>();
    private final List<Map<String, Object>> cases = new ArrayList<>();
    private StoneSearchQualificationFixture fixture;
    private StoneChatbotService chatbot;
    private StoneChatConversationRepository history;
    private StoneChatSessionRepository sessions;
    private StoneChatAvailabilityService availability;
    private StoneChatConversationSerializationService serialization;
    private ModelInvocationAdmissionService admission;
    private StoneDocumentEmbeddingService embedding;
    private StoneStructuredModelService guard;
    private String activeCase;
    private boolean raceDone;
    private boolean failGeneration;
    private UUID runId;
    private String lastStatus = "NOT_STARTED";
    private final long dailyBudget = qualificationDailyBudget(System.getenv("MODEL_DAILY_TOKEN_BUDGET"));
    private boolean diagnosticMode;
    private final List<JsonNode> setupAttempts = new ArrayList<>();
    private final Path results = Path.of("../specs/0027-llm-search-query-qualification/qualification-results.json");
    private final Path report = Path.of("../specs/0027-llm-search-query-qualification/qualification-report.md");

    @Test void verifyFixtureAndHarness() throws Exception {
        prepareRedisConnection(); redis.probe(key());
        try (var approved = new StoneSearchQualificationFixture()) {
            JsonNode data = approved.resource();
            assertThat(data.path("cards").size()).isEqualTo(15); assertThat(data.path("passages").size()).isEqualTo(7);
            assertThat(data.path("scenarios").size()).isEqualTo(20);
            int turns = 0, semantic = 0, answers = 0, controlled = 0;
            List<String> keys = new ArrayList<>();
            for (JsonNode card : data.path("cards")) {
                keys.add(card.path("key").asString()); StoneType.valueOf(card.path("type").asString()); StoneSize.valueOf(card.path("size").asString()); AdoptionStatus.valueOf(card.path("status").asString());
                assertThat(Instant.parse(card.path("admissionDate").asString())).isNotNull();
            }
            assertThat(keys).doesNotHaveDuplicates().containsExactly("B01","B02","B03","B04","B05","B06","B07","B08","B09","B10","G01","G02","G03","P01","O01");
            for (JsonNode scenario : data.path("scenarios")) {
                turns += scenario.path("queries").size();
                if (cohort(scenario, "R")) semantic++;
                if (cohort(scenario, "A")) answers++;
                if (scenario.path("controlled").asBoolean()) controlled++;
                for (JsonNode query : scenario.path("queries")) assertThat(query.asString()).isNotBlank();
            }
            assertThat(turns).isEqualTo(28); assertThat(semantic).isEqualTo(9); assertThat(answers).isEqualTo(12); assertThat(controlled).isEqualTo(3);
            assertThat(data.path("cards").get(4).path("admissionDate")).isEqualTo(data.path("cards").get(5).path("admissionDate"));
            assertThat(approved.calendar().instant()).isEqualTo(Instant.parse("2026-10-13T12:00:00Z"));
            assertThat(realClock).isNotSameAs(approved.calendar());
            assertThat(quality("Q027-009", 0, List.of("B01","B02","B03","B04","B05"))).isTrue();
            assertThat(quality("Q027-009", 0, List.of("B10"))).isFalse();
            assertThat(quality("Q027-007", 0, List.of("G01","B01"))).isTrue();
            assertThat(quality("Q027-007", 0, List.of("B01"))).isFalse();
            assertThat(quality("Q027-007", 0, List.of("G01","B01","G02"))).isFalse();
            assertThat(quality("Q027-012", 0, List.of("B10","B05","B06","B04","B03"))).isTrue();
            assertThat(quality("Q027-012", 0, List.of("G01"))).isFalse();
            assertThat(quality("Q027-017", 0, List.of("B06","B04","B03"))).isTrue();
            assertThat(quality("Q027-017", 0, List.of("B10","B06","B04","B03"))).isFalse();
            assertThat(quality("Q027-018", 0, List.of())).isTrue();
            assertThatThrownBy(() -> diagnosticIds(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> diagnosticIds("Q027-999")).isInstanceOf(IllegalArgumentException.class);
            approved.connect();
            long reservations = approved.jdbcTemplate().queryForObject("SELECT count(*) FROM model_token_reservation", Long.class);
            assertThat(approved.jdbcTemplate().queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class)).isEqualTo("0.8.6");
            approved.seed();
            assertThat(approved.ids()).hasSize(15);
            for (JsonNode card : data.path("cards")) {
                var actual = approved.jdbcTemplate().queryForMap("SELECT name,stone_type,size,adoption_status,biography FROM stone WHERE id=?", approved.ids().get(card.path("key").asString()));
                assertThat(actual.get("name")).isEqualTo(card.path("name").asString()); assertThat(actual.get("stone_type")).isEqualTo(card.path("type").asString());
                assertThat(actual.get("size")).isEqualTo(card.path("size").asString()); assertThat(actual.get("adoption_status")).isEqualTo(card.path("status").asString());
                assertThat(actual.get("biography")).isEqualTo(card.path("biography").isNull() ? null : card.path("biography").asString());
            }
            fixture = approved;
            StoneChatTurnEntity largeTurn = mock(StoneChatTurnEntity.class);
            when(largeTurn.getText()).thenReturn("Basalt 07 is large.");
            assertThat(hardViolations("Q027-019", 4, List.of("B07"), largeTurn))
                    .doesNotContain("WRONG_SIZE:B07").contains("WRONG_DATE_OR_REFERENCE:B07", "EXACT_OR_REFERENCE_ORACLE");
            assertThat(hardViolations("Q027-019", 2, List.of("B07"), largeTurn)).contains("WRONG_SIZE:B07");
            approved.reserve("B06"); approved.delete("B05");
            assertThat(approved.jdbcTemplate().queryForObject("SELECT adoption_status FROM stone WHERE id=?", String.class, approved.ids().get("B06"))).isEqualTo("RESERVED");
            assertThat(approved.jdbcTemplate().queryForObject("SELECT count(*) FROM stone WHERE id=?", Long.class, approved.ids().get("B05"))).isZero();
            assertThat(approved.jdbcTemplate().queryForObject("SELECT count(*) FROM model_token_reservation", Long.class)).isEqualTo(reservations);

        }
    }

    private JsonNode retainedOriginal;
    private String retainedReport;
    private final List<Map<String, Object>> correctionDiagnostics = new ArrayList<>();

    @Test void continueAfterApprovedProfileCorrection() throws Exception {
        if (!"approved-profile-correction".equals(System.getenv("STONE_QUALIFICATION_ATTEMPT")))
            throw new IllegalStateException("Explicit separately agreed correction continuation required");
        retainedOriginal = jsonMapper.readTree(Files.readString(results)); retainedReport = Files.readString(report);
        if (!retainedOriginal.path("status").asString().equals("INCOMPLETE_CONTROL_OUTAGE") || retainedOriginal.path("cases").size() != 1
                || !retainedOriginal.at("/cases/0/id").asString().equals("Q027-001") || retainedOriginal.has("priorOriginalEvidence"))
            throw new IllegalStateException("Only the retained first profile outage can be continued once");
        cases.add(jsonMapper.convertValue(retainedOriginal.path("cases").get(0), new TypeReference<Map<String, Object>>() {}));
        runId = UUID.randomUUID(); fixture = new StoneSearchQualificationFixture();
        try {
            fixture.connect(); configure(); fixture.seed();
            fixture.restoreCompatibleVectors(jsonMapper.convertValue(retainedOriginal.path("compatibleFixtureVectors"), new TypeReference<Map<String, String>>() {}));
            diagnosticMode = true;
            Map<String, Object> diagnosis = collectScenario(fixture.resource().path("scenarios").get(0));
            diagnosis.put("hypothesis", "Established digest-qualified producer profile is now accepted and fixture source metadata is consistent; unchanged original outcome remains failed");
            correctionDiagnostics.add(diagnosis); diagnosticMode = false;
            if (writeInterruptedEvidence(diagnosis, "INCOMPLETE_CORRECTION_CONTROL_OUTAGE")) return;
            for (JsonNode scenario : fixture.resource().path("scenarios")) if (!scenario.path("id").asString().equals("Q027-001")) {
                Map<String, Object> collected = collectScenario(scenario);
                if (writeInterruptedEvidence(collected, "INCOMPLETE_CONTROL_OUTAGE_AFTER_CORRECTION")) return;
                writeEvidence("BASELINE_CONTINUATION_IN_PROGRESS");
            }
            writeEvidence("BASELINE_COLLECTED_REVIEW_PENDING");
        } catch (Exception | AssertionError failure) {
            if (fixture.jdbcTemplate() != null) writeEvidence("INCOMPLETE_CORRECTION_" + failure.getClass().getSimpleName());
            throw failure;
        } finally { if (serialization != null) serialization.close(); if (fixture != null) fixture.close(); }
    }

    @Test void resumeUnattemptedOriginalCases() throws Exception {
        if (!"resume-unattempted".equals(System.getenv("STONE_QUALIFICATION_ATTEMPT"))) throw new IllegalStateException("Explicit original-case continuation required");
        retainedOriginal = jsonMapper.readTree(Files.readString(results)); retainedReport = Files.readString(report);
        if (!retainedOriginal.path("status").asString().startsWith("INCOMPLETE") || !retainedOriginal.has("priorOriginalEvidence"))
            throw new IllegalStateException("Retained approved correction/outage evidence required");
        for (JsonNode prior : retainedOriginal.path("cases")) cases.add(jsonMapper.convertValue(prior, new TypeReference<Map<String, Object>>() {}));
        for (JsonNode prior : retainedOriginal.path("correctionDiagnostics")) correctionDiagnostics.add(jsonMapper.convertValue(prior, new TypeReference<Map<String, Object>>() {}));
        Set<String> attempted = cases.stream().map(value -> value.get("id").toString()).collect(Collectors.toSet());
        runId = UUID.randomUUID(); fixture = new StoneSearchQualificationFixture();
        try {
            fixture.connect(); configure(); fixture.seed();
            fixture.restoreCompatibleVectors(jsonMapper.convertValue(retainedOriginal.path("compatibleFixtureVectors"), new TypeReference<Map<String, String>>() {}));
            for (JsonNode scenario : fixture.resource().path("scenarios")) if (!attempted.contains(scenario.path("id").asString())) {
                Map<String, Object> collected = collectScenario(scenario);
                if (writeInterruptedEvidence(collected, "INCOMPLETE_UNATTEMPTED_CASE_CONTINUATION_OUTAGE")) return;
                writeEvidence("BASELINE_CONTINUATION_IN_PROGRESS");
            }
            writeEvidence("BASELINE_COLLECTED_REVIEW_PENDING");
        } catch (Exception | AssertionError failure) {
            if (fixture.jdbcTemplate() != null) writeEvidence("INCOMPLETE_CONTINUATION_" + failure.getClass().getSimpleName()); throw failure;
        } finally { if (serialization != null) serialization.close(); if (fixture != null) fixture.close(); }
    }

    @Test void verifyRetainedRealProfileSqlStage() throws Exception {
        ObjectNode retained = (ObjectNode) jsonMapper.readTree(Files.readString(results));
        if (retained.has("realProfileSqlStage")) throw new IllegalStateException("Real profile SQL stage already recorded");
        fixture = new StoneSearchQualificationFixture();
        try {
            fixture.connect(); fixture.seed(); fixture.restoreCompatibleVectors(jsonMapper.convertValue(retained.path("compatibleFixtureVectors"), new TypeReference<Map<String, String>>() {}));
            JsonNode coordinates = jsonMapper.readTree(fixture.vectorCache().get("E01")); double[] vector = new double[1024];
            for (int index = 0; index < vector.length; index++) vector[index] = coordinates.get(index).asDouble();
            var query = new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL, StoneSearchQualificationFixture.PROFILE, vector, 0);
            var passages = fixture.bean(StoneSemanticSearchRepository.class).findPassages(new StoneSemanticPassageCriteria(query, List.of(StoneType.BASALT), new StoneSemanticPassageCriteria.Bounds(10, 0.5)));
            assertThat(passages).isNotEmpty(); assertThat(passages.getFirst().fragment().getFragmentOrder()).isEqualTo(1); assertThat(passages.getFirst().score()).isGreaterThan(0.999);
            var exact = new StoneExactSearchCriteria(); exact.setStoneTypes(List.of(StoneType.BASALT));
            var candidates = fixture.bean(StoneSemanticSearchRepository.class).search(new StoneSemanticRetrievalCriteria(new StoneSemanticSearchCriteria(exact, List.of(new StoneSemanticWish("use", "shelf"))), query, passages, new StoneSemanticRetrievalCriteria.Bounds(10, 0.5)));
            assertThat(candidates.candidates()).isNotEmpty();
            retained.set("realProfileSqlStage", jsonMapper.valueToTree(Map.of("status", "PASS", "profile", query.profile(), "source", "unchanged real E01 embedding from original fixture", "naturalQueryQuality", "NOT_EVALUATED", "newModelCalls", 0)));
            Files.writeString(results, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(retained) + "\n");
            Files.writeString(report, "\n## Real Profile SQL Stage\n\nPASS: restored actual digest-qualified card/document vectors accept an unchanged real E01 query and return its source at cosine similarity above 0.999. No new inference, cohort score change or ledger deletion.\n", StandardOpenOption.APPEND);
        } finally { fixture.close(); }
    }

    @Test void qualifyApprovedBaseline() throws Exception {
        if (!"original-baseline".equals(System.getenv("STONE_QUALIFICATION_ATTEMPT"))) throw new IllegalStateException("Explicit original-baseline execution is required");
        if (Files.exists(results)) {
            JsonNode previous = jsonMapper.readTree(Files.readString(results));
            if (!previous.path("status").asString().startsWith("INCOMPLETE") || !previous.path("cases").isEmpty()
                    || !previous.at("/accounting/reservations").isEmpty() || !previous.path("compatibleFixtureVectors").isEmpty())
                throw new IllegalStateException("Original attempted evidence exists; never replace it by rerunning a baseline");
            setupAttempts.add(previous);
        }
        runId = UUID.randomUUID(); fixture = new StoneSearchQualificationFixture();
        try {
            fixture.connect();
            if (!setupAttempts.isEmpty() && fixture.jdbcTemplate().queryForObject("SELECT count(*) FROM model_token_reservation", Long.class) != 0)
                throw new IllegalStateException("Actual usage exists; refusing a baseline setup restart");
            configure(); writeEvidence("PREPARING_FIXTURE"); fixture.seed(); fixture.embed(embedding, realClock);
            for (JsonNode scenario : fixture.resource().path("scenarios")) {
                Map<String, Object> collected = collectScenario(scenario);
                if (writeInterruptedEvidence(collected, "INCOMPLETE_CONTROL_OUTAGE")) return;
                writeEvidence("BASELINE_IN_PROGRESS");
            }
            writeEvidence("BASELINE_COLLECTED_REVIEW_PENDING");
        } catch (Exception | AssertionError failure) {
            if (fixture.jdbcTemplate() != null) writeEvidence("INCOMPLETE_" + failure.getClass().getSimpleName());
            throw failure;
        } finally {
            if (serialization != null) serialization.close();
            if (fixture != null) fixture.close();
        }
    }

    @Test void verifyClosedOperationClassification() {
        chatbot = mock(StoneChatbotService.class);
        UUID conversation = UUID.randomUUID();
        assertThat(subsequentHistoryClosed(conversation, "test-owned-session")).isFalse();
        when(chatbot.findById(conversation, "test-owned-session")).thenThrow(new StoneChatUnavailableException());
        assertThat(subsequentHistoryClosed(conversation, "test-owned-session")).isTrue();
    }

    private boolean subsequentHistoryClosed(UUID conversation, String session) {
        try { chatbot.findById(conversation, session); return false; }
        catch (StoneChatUnavailableException failure) { return true; }
    }

    private static long qualificationDailyBudget(String configured) {
        return new ModelInvocationSettings(new MockEnvironment().withProperty("MODEL_DAILY_TOKEN_BUDGET", configured == null ? "200000" : configured)).dailyBudget();
    }

    private static boolean tokenBudgetExhausted(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ModelTokenBudgetExceededException) return true;
        }
        return false;
    }

    private Map<String, Object> recordBudgetInterruption(Map<String, Object> observed, Throwable failure) {
        if (!tokenBudgetExhausted(failure) && stages.stream().noneMatch(stage -> "TOKEN_BUDGET_EXHAUSTED".equals(stage.get("stage")))) return observed;
        observed.put("budgetExhausted", true); observed.put("verificationStatus", "INCOMPLETE_BUDGET_EXHAUSTED");
        observed.put("answerQuality", "INCOMPLETE_BUDGET_EXHAUSTED");
        @SuppressWarnings("unchecked") List<String> violations = (List<String>) observed.getOrDefault("hardViolations", List.of());
        observed.put("hardViolations", violations.stream().filter(violation -> !violation.equals("REQUEST_DID_NOT_COMPLETE")).toList());
        if ("ERROR".equals(observed.get("outcome"))) observed.remove("candidateOracle");
        return observed;
    }

    private boolean writeInterruptedEvidence(Map<String, Object> collected, String outageStatus) throws Exception {
        if (Boolean.TRUE.equals(collected.get("budgetExhausted"))) { writeEvidence("INCOMPLETE_BUDGET_EXHAUSTED"); return true; }
        if (Boolean.TRUE.equals(collected.get("controlOutage"))) { writeEvidence(outageStatus); return true; }
        return false;
    }

    private StoneRagAnswerService observedAnswers(StoneRagAnswerProvider provider, StoneRagGroundingService grounding, StoneRagSettings settings) {
        return new StoneRagAnswerService(provider, new StoneRagAnswerValidationService(), grounding,
                new StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper(new StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper()), settings, realClock) {
            @Override public StoneRagAnswerResultEntity prepare(StoneRagGenerationRequest request, StoneRagExecutionEntity execution) {
                StoneRagAnswerResultEntity stoneRagAnswerResultEntity = super.prepare(request, execution);
                if (tokenBudgetExhausted(stoneRagAnswerResultEntity.failure())) stages.add(Map.of("stage", "TOKEN_BUDGET_EXHAUSTED"));
                return stoneRagAnswerResultEntity;
            }
        };
    }

    private static String answerAssessmentStatus(StoneRagCompletionKind completion, boolean answerCohort) {
        if (!answerCohort) return "NOT_IN_COHORT";
        return completion == StoneRagCompletionKind.GENERATED ? "AUTONOMOUS_REVIEW_PENDING" : "FAIL_TEMPLATE_OR_MISSING_GENERATION";
    }

    @Test void verifyAutonomousReviewPolicy() {
        assertThat(answerAssessmentStatus(StoneRagCompletionKind.GENERATED, true)).isEqualTo("AUTONOMOUS_REVIEW_PENDING");
        assertThat(answerAssessmentStatus(StoneRagCompletionKind.TEMPLATE, true)).isEqualTo("FAIL_TEMPLATE_OR_MISSING_GENERATION");
        assertThat(answerAssessmentStatus(StoneRagCompletionKind.GENERATED, false)).isEqualTo("NOT_IN_COHORT");
        assertThat(recordBudgetInterruption(new LinkedHashMap<>(Map.of("answerQuality", answerAssessmentStatus(StoneRagCompletionKind.TEMPLATE, true))),
                new ModelTokenBudgetExceededException(realClock.instant().plusSeconds(60))))
                .containsEntry("answerQuality", "INCOMPLETE_BUDGET_EXHAUSTED");
    }

    @Test void verifyTokenBudgetConfiguration() {
        assertThat(qualificationDailyBudget(null)).isEqualTo(200000);
        assertThat(qualificationDailyBudget("250000")).isEqualTo(250000);
        assertThat(new ModelInvocationSettings(new MockEnvironment()).dailyBudget()).isEqualTo(100000);
        assertThatThrownBy(() -> qualificationDailyBudget("0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> qualificationDailyBudget("-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> qualificationDailyBudget("invalid")).isInstanceOf(RuntimeException.class);
    }

    @Test void verifyQuotaInterruptionClassification() {
        ModelTokenBudgetExceededException modelTokenBudgetExceededException = new ModelTokenBudgetExceededException(realClock.instant().plusSeconds(60));
        Map<String, Object> direct = new LinkedHashMap<>(Map.of("outcome", "ERROR", "candidateOracle", false,
                "answerQuality", "FAIL_MISSING_GENERATION", "hardViolations", List.of("REQUEST_DID_NOT_COMPLETE", "VISIBLE_PROVENANCE")));
        assertThat(recordBudgetInterruption(direct, new StoneChatUnavailableException(modelTokenBudgetExceededException)))
                .containsEntry("verificationStatus", "INCOMPLETE_BUDGET_EXHAUSTED").containsEntry("answerQuality", "INCOMPLETE_BUDGET_EXHAUSTED")
                .doesNotContainKey("candidateOracle");
        assertThat(direct.get("hardViolations")).isEqualTo(List.of("VISIBLE_PROVENANCE"));

        MockEnvironment environment = new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://qualification.invalid");
        StoneRagGenerationRequest stoneRagGenerationRequest = new StoneRagGenerationRequest();
        stoneRagGenerationRequest.setMode(StoneRagMode.SELECTION); stoneRagGenerationRequest.setCandidates(List.of(new StoneRagCandidateRequest()));
        stoneRagGenerationRequest.setDeadline(realClock.instant().plusSeconds(115));
        StoneRagExecutionEntity stoneRagExecutionEntity = new StoneRagExecutionEntity();
        stoneRagExecutionEntity.setDeadline(realClock.instant().plusSeconds(120)); stoneRagExecutionEntity.setModelDeadline(stoneRagGenerationRequest.getDeadline());
        StoneRagAnswerResultEntity stoneRagAnswerResultEntity = observedAnswers((request, repair) -> { throw modelTokenBudgetExceededException; },
                mock(StoneRagGroundingService.class), new StoneRagSettings(environment, new StoneExactSearchSettings(environment)))
                .prepare(stoneRagGenerationRequest, stoneRagExecutionEntity);
        assertThat(stoneRagAnswerResultEntity.completion()).isEqualTo(StoneRagCompletionKind.TEMPLATE);
        assertThat(recordBudgetInterruption(new LinkedHashMap<>(Map.of("outcome", "COMPLETED", "answerQuality", "FAIL_TEMPLATE_OR_MISSING_GENERATION")), null))
                .containsEntry("outcome", "COMPLETED").containsEntry("answerQuality", "INCOMPLETE_BUDGET_EXHAUSTED");
        stages.clear();
        Map<String, Object> malformed = new LinkedHashMap<>(Map.of("outcome", "ERROR", "answerQuality", "FAIL_MISSING_GENERATION"));
        assertThat(recordBudgetInterruption(malformed, new InvalidStoneRagOutputException())).doesNotContainKey("budgetExhausted")
                .containsEntry("answerQuality", "FAIL_MISSING_GENERATION");
    }

    private Map<String, Object> collectScenario(JsonNode scenario) throws Exception {
                activeCase = scenario.path("id").asString(); raceDone = false; failGeneration = false; fixture.restore();
                String session = sessions.findOrCreate(null).id(); UUID conversation = UUID.randomUUID(); sessions.authorize(session, conversation, true);
                keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), StoneChatSessionRepository.ownerKey(conversation),
                        StoneChatActiveTurnRepository.key(session, conversation), StoneChatConversationRepository.key(session, conversation)));
                Map<String, Object> collected = new LinkedHashMap<>(); collected.put("id", activeCase); collected.put("attempt", diagnosticMode ? "DIAGNOSTIC" : "ORIGINAL_BASELINE");
                collected.put("queries", scenario.path("queries")); collected.put("expected", scenario.path("expected")); collected.put("cohorts", scenario.path("cohorts"));
                collected.put("controlled", scenario.path("controlled").asBoolean()); collected.put("humanReview", "DEFERRED_OUT_OF_SCOPE");
                List<Map<String, Object>> turns = new ArrayList<>(); collected.put("turns", turns); if (!diagnosticMode) cases.add(collected);
                for (int turnIndex = 0; turnIndex < scenario.path("queries").size(); turnIndex++) {
                    if (activeCase.equals("Q027-016") && turnIndex == 1) fixture.reserve("B06");
                    if (activeCase.equals("Q027-020") && turnIndex == 1) { failGeneration = false; availability.recover(); }
                    if (activeCase.equals("Q027-020")) failGeneration = true;
                    stages.clear(); Map<String, Object> observed = new LinkedHashMap<>(); turns.add(observed);
                    observed.put("input", scenario.path("queries").get(turnIndex).asString()); observed.put("turnIndex", turnIndex + 1);
                    long before = fixture.jdbcTemplate().queryForObject("SELECT count(*) FROM model_token_reservation", Long.class);
                    try {
                        StoneChatMessageResponse answer = chatbot.sendMessage(new StoneChatMessageRequest(conversation, UUID.randomUUID(), scenario.path("queries").get(turnIndex).asString(), null), session, () -> {});
                        var saved = history.findById(session, conversation).turns(); var completed = saved.getLast();
                        observed.put("outcome", "COMPLETED"); observed.put("answer", answer); observed.put("route", completed.getRagState());
                        observed.put("effectiveCriteria", completed.getExactSearchCriteria()); observed.put("semanticState", completed.getSemanticSearchState());
                        observed.put("checkedCandidates", completed.getSemanticCandidates());
                        List<String> selected = answer.stones().stream().map(stone -> fixture.key(stone.id())).toList(); observed.put("selectedKeys", selected);
                        observed.put("candidateOracle", quality(activeCase, turnIndex, selected));
                        observed.put("hardViolations", hardViolations(activeCase, turnIndex, selected, completed));
                        observed.put("answerQuality", answerAssessmentStatus(completed.getRagState().completion(), cohort(scenario, "A")));
                        if (activeCase.equals("Q027-020") && turnIndex == 0) {
                            boolean closed = subsequentHistoryClosed(conversation, session);
                            observed.put("subsequentHistoryClosed", closed);
                            if (!closed) {
                                @SuppressWarnings("unchecked") var violations = new ArrayList<>((List<String>) observed.get("hardViolations"));
                                violations.add("SUBSEQUENT_OPERATIONS_NOT_CLOSED"); observed.put("hardViolations", List.copyOf(violations));
                            }
                        }
                    } catch (RuntimeException failure) {
                        observed.put("outcome", "ERROR"); observed.put("exception", failure.getClass().getSimpleName());
                        List<Map<String, Object>> causes = new ArrayList<>();
                        for (Throwable cause = failure; cause != null && causes.size() < 8; cause = cause.getCause()) {
                            causes.add(Map.of("type", cause.getClass().getName(), "frames", Arrays.stream(cause.getStackTrace()).filter(frame -> frame.getClassName().startsWith("lab.stoneshelter.")).limit(8).map(Object::toString).toList()));
                        }
                        observed.put("safeCauses", causes); observed.put("candidateOracle", activeCase.equals("Q027-020") && turnIndex == 1 && failure instanceof StoneChatUnavailableException && stages.stream().anyMatch(stage -> "CONTROLLED_GENERATION_FAILURE".equals(stage.get("stage"))));
                        observed.put("hardViolations", List.of(activeCase.equals("Q027-020") && turnIndex == 1 && failure instanceof StoneChatUnavailableException && stages.stream().anyMatch(stage -> "CONTROLLED_GENERATION_FAILURE".equals(stage.get("stage"))) ? "EXPECTED_KNOWLEDGE_GENERATION_FAILURE" : "REQUEST_DID_NOT_COMPLETE"));
                        observed.put("answerQuality", cohort(scenario, "A") ? "FAIL_MISSING_GENERATION" : "NOT_IN_COHORT");
                        observed = recordBudgetInterruption(observed, failure);
                    }
                    observed = recordBudgetInterruption(observed, null);
                    observed.put("stages", List.copyOf(stages)); observed.put("newReservations", fixture.jdbcTemplate().queryForObject("SELECT count(*) FROM model_token_reservation", Long.class) - before);
                    observed.put("completedPairs", history.findById(session, conversation).turns().size()); observed.put("accounting", fixture.accounting());
                    System.out.println("Q027 original " + activeCase + " turn=" + (turnIndex + 1) + " outcome=" + observed.get("outcome") + " keys=" + observed.get("selectedKeys") + " generation=" + observed.get("answerQuality"));
                    if (Boolean.TRUE.equals(observed.get("budgetExhausted"))) {
                        collected.put("budgetExhausted", true); collected.put("verificationStatus", "INCOMPLETE_BUDGET_EXHAUSTED");
                        if (!diagnosticMode) writeEvidence("INCOMPLETE_BUDGET_EXHAUSTED");
                        return collected;
                    }
                    if (!diagnosticMode) writeEvidence("BASELINE_IN_PROGRESS");
                    if (!activeCase.equals("Q027-020")) {
                        try { availability.assertReady(); } catch (StoneChatUnavailableException unavailable) { collected.put("controlOutage", true); return collected; }
                    }
                }
                collected.put("candidateOracle", turns.stream().allMatch(turn -> Boolean.TRUE.equals(turn.get("candidateOracle"))));
                return collected;
    }

    private void configure() {
        prepareRedisConnection();
        MockEnvironment configuration = new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", required("CHAT_MODEL_OLLAMA_BASE_URL"))
                .withProperty("STONE_DOCUMENT_OLLAMA_BASE_URL", required("STONE_DOCUMENT_OLLAMA_BASE_URL"))
                .withProperty("MODEL_MAX_CONCURRENT_CALLS", "1").withProperty("MODEL_DAILY_TOKEN_BUDGET", Long.toString(dailyBudget))
                .withProperty("CHAT_MODEL_CONTEXT_TOKENS", "8192").withProperty("CHAT_MODEL_OUTPUT_TOKENS", "512")
                .withProperty("CHAT_MODEL_CALL_TIMEOUT", "PT50S").withProperty("CHAT_TURN_DEADLINE", "PT120S");
        var modelSettings = new StoneExactSearchSettings(configuration); var semanticSettings = new StoneSemanticSearchSettings(configuration, new StoneCardSearchSettings(configuration));
        var ragSettings = new StoneRagSettings(configuration, modelSettings);
        admission = new ModelInvocationAdmissionService(fixture.bean(ModelTokenBudgetTransactionService.class), new ModelInvocationSettings(configuration), realClock);
        var transport = new OllamaChatRepository() {
            @Override public StoneModelResponseEntity invoke(StoneModelInvocationEntity invocation) {
                String kind = invocation.getSchema().containsKey("properties") ? jsonMapper.writeValueAsString(invocation.getSchema()) : "";
                String stage = kind.contains("judgements") ? "EVIDENCE" : kind.contains("introduction") ? "GENERATION" : kind.contains("valid") ? "GROUNDING" : "INTERPRETATION";
                if (failGeneration && stage.equals("GENERATION")) { stages.add(Map.of("stage", "CONTROLLED_GENERATION_FAILURE")); throw new IllegalStateException("Controlled final-generation-only outage"); }
                var response = super.invoke(invocation); stages.add(Map.of("stage", stage, "system", invocation.getSystem(), "currentInput", invocation.getMessage(), "rawOutput", response.text(), "inputTokens", response.inputTokens(), "outputTokens", response.outputTokens()));
                return response;
            }
        };
        guard = new StoneStructuredModelService(transport, admission, modelSettings, realClock);
        embedding = new StoneDocumentEmbeddingService(new OllamaEmbeddingRepository(), new StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper(new StoneDocumentIndexSettings(configuration), realClock));
        embedding.configureAdmission(admission, realClock);
        sessions = new StoneChatSessionRepository(redis, settings); history = new StoneChatConversationRepository(redis, settings);
        var active = new StoneChatActiveTurnRepository(redis, settings); serialization = new StoneChatConversationSerializationService(active, settings);
        var limiter = new StoneChatRateLimiterRepository(redis, settings);
        availability = new StoneChatAvailabilityService(sessions, history, limiter, active, redis, serialization, new StoneChatContextSelector());
        var semanticProvider = new OllamaSemanticSearchProvider(guard, fixture.calendar(), new StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper());
        availability.configureModels(new StoneExactSearchProvider() {
            @Override public StoneModelResponseEntity invoke(StoneChatTurnEntity turn, List<StoneChatTurnEntity> previous, Instant deadline, String repair) { return semanticProvider.invoke(turn, previous, deadline, repair); }
            @Override public void probe() { transport.probe(required("CHAT_MODEL_OLLAMA_BASE_URL")); }
        }, admission);
        availability.configureRagControls(fixture.bean(StoneRagKnowledgeRepository.class)); availability.recover();
        StoneSemanticSearchRepository retrieval = mock(StoneSemanticSearchRepository.class, call -> {
            try {
                Object result = call.getMethod().invoke(fixture.bean(StoneSemanticSearchRepository.class), call.getArguments());
                if (call.getMethod().getName().equals("findPassages")) stages.add(Map.of("stage", "PASSAGE_RETRIEVAL", "result", result));
                if (call.getMethod().getName().equals("searchAlternatives")) {
                    @SuppressWarnings("unchecked") var alternatives = (List<StoneSemanticCandidateEntity>) result;
                    stages.add(Map.of("stage", "EXACT_ALTERNATIVES", "keys", alternatives.stream().map(candidate -> fixture.key(candidate.getStone().getId())).toList()));
                }
                if (call.getMethod().getName().equals("findEligible")) {
                    @SuppressWarnings("unchecked") var eligible = (List<StoneEntity>) result;
                    stages.add(Map.of("stage", "CURRENT_ELIGIBILITY", "requestedIds", call.getArgument(0), "eligibleKeys", eligible.stream().map(stone -> fixture.key(stone.getId())).toList()));
                }
                if (call.getMethod().getName().equals("search")) {
                    StoneSemanticRetrievalResultEntity retrieved = (StoneSemanticRetrievalResultEntity) result;
                    stages.add(Map.of("stage", call.getMethod().getName(), "retrieved", retrieved.candidates().stream().map(candidate -> Map.of("id", candidate.getStone().getId(), "key", fixture.key(candidate.getStone().getId()), "cardScore", candidate.getCardScore() == null ? "NONE" : candidate.getCardScore(), "passages", candidate.getPassages())).toList()));
                }
                return result;
            } catch (InvocationTargetException exception) { throw exception.getCause(); }
        });
        var evidence = new StoneSemanticEvidenceService(new OllamaSemanticEvidenceProvider(guard), new StoneSemanticEvidenceValidationService(), new StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper(), new OllamaSemanticEvidenceProvider(guard), semanticSettings, realClock, new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper());
        var candidates = new StoneSemanticCandidateService(retrieval, evidence, embedding, new StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper(), semanticSettings, realClock);
        StoneExactSearchRepository exactRepository = mock(StoneExactSearchRepository.class, call -> {
            try {
                Object result = call.getMethod().invoke(fixture.bean(StoneExactSearchRepository.class), call.getArguments());
                if (result instanceof StoneExactSearchResultEntity found) stages.add(Map.of("stage", "EXACT_RETRIEVAL", "total", found.getTotal(), "keys", found.getStones().stream().map(stone -> fixture.key(stone.getId())).toList()));
                return result;
            } catch (InvocationTargetException exception) { throw exception.getCause(); }
        });
        var exact = new StoneExactSearchService(mock(StoneExactSearchProvider.class), new StoneExactSearchValidationService(), exactRepository, new StoneExactSearchResultEntityToStoneChatTurnEntityMapper(), new StoneExactSearchDecisionToStoneExactSearchCriteriaMapper(), fixture.calendar());
        var semantic = new StoneSemanticSearchService(semanticProvider, new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()), exact, candidates, new StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper(), new StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper(), new StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper(), realClock);
        var knowledge = new StoneRagKnowledgeService(fixture.bean(StoneRagKnowledgeRepository.class), retrieval, embedding, new StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper(), semanticSettings, realClock);
        StoneRagGroundingProvider checking = (request, repair) -> Set.of("Q027-017", "Q027-018").contains(activeCase) ? new StoneModelResponseEntity("{\"valid\":true,\"reason\":\"Controlled valid draft for race boundary\"}", 0, 0) : new OllamaRagGroundingProvider(guard).invoke(request, repair);
        StoneRagAnswerProvider generation = (request, repair) -> {
            if (!Set.of("Q027-017", "Q027-018").contains(activeCase)) return new OllamaRagAnswerProvider(guard, ragSettings).invoke(request, repair);
            var value = jsonMapper.createObjectNode().put("introduction", "Available options."); var stones = value.putArray("stones"); value.putNull("knowledge");
            for (var candidate : request.getCandidates()) stones.addObject().put("id", candidate.getId()).put("explanation", "It matches the requested basalt type and small size.");
            if (!raceDone) { raceDone = true; if (activeCase.equals("Q027-017")) { fixture.reserve("B10"); fixture.delete("B05"); } else { fixture.reserve("B05"); fixture.reserve("B06"); } }
            stages.add(Map.of("stage", "CONTROLLED_VALID_GENERATION", "mutation", activeCase)); return new StoneModelResponseEntity(value.toString(), 0, 0);
        };
        var grounding = new StoneRagGroundingService(checking, new OllamaRagGroundingProvider(guard), new StoneRagGenerationRequestToStoneRagGroundingRequestMapper(), new StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper(), semanticSettings, realClock);
        var answers = observedAnswers(generation, grounding, ragSettings);
        var decisionMapper = new StoneRagDecisionToStoneSemanticSearchDecisionMapper();
        var routing = new OllamaRagRoutingProvider(guard, semanticProvider, fixture.calendar(), new StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper());
        var rag = new StoneRagConversationService(routing, new StoneRagDecisionValidationService(new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()), decisionMapper), decisionMapper, semantic, candidates, exact, new StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper(), knowledge, fixture.bean(StoneRagKnowledgeRepository.class), new StoneChatReferenceService(), new StoneRagDecisionToStoneRagAnswerCriteriaMapper(), new StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper(), new StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper(), answers, availability, realClock, ragSettings);
        var admissionService = new StoneChatAdmissionService(availability, mock(StoneChatOriginService.class), mock(StoneChatClientAddressService.class), limiter, sessions, settings);
        chatbot = new StoneChatbotService(history, serialization, admissionService, availability, new StoneChatMessageRequestToStoneChatTurnEntityMapper(), new StoneChatTurnEntityToStoneChatMessageResponseMapper(), new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector()); chatbot.configureRagSearch(rag, realClock, settings);
    }
    private static boolean cohort(JsonNode scenario, String key) { for (JsonNode value : scenario.path("cohorts")) if (value.asString().equals(key)) return true; return false; }
    private static boolean quality(String id, int turn, List<String> selected) {
        return switch (id) {
            case "Q027-001" -> selected.equals(List.of("B10","B07","B05","B06","B04"));
            case "Q027-002", "Q027-012" -> selected.equals(List.of("B10","B05","B06","B04","B03"));
            case "Q027-003" -> selected.equals(List.of("B05","B06"));
            case "Q027-004", "Q027-005", "Q027-006", "Q027-013", "Q027-018" -> selected.isEmpty();
            case "Q027-007", "Q027-008", "Q027-011" -> !selected.isEmpty() && Set.of("B01","B02","B03","B04","B05","B06","B07","G01","G02").containsAll(selected)
                    && (selected.contains("G01") || selected.contains("G02")) && cardBeforeType(selected);
            case "Q027-009", "Q027-010" -> selected.equals(List.of("B01","B02","B03","B04","B05"));
            case "Q027-014" -> selected.equals(turn == 0 ? List.of("B01","B02","B03","B04","B05") : List.of("B07"));
            case "Q027-015" -> turn == 0 ? selected.equals(List.of("B01","B02","B03","B04","B05")) : !selected.isEmpty() && selected.getFirst().equals("G03") && Set.of("G03","G01","G02","P01").containsAll(selected);
            case "Q027-016" -> selected.equals(turn == 0 ? List.of("B05","B06") : List.of());
            case "Q027-017" -> selected.equals(List.of("B06","B04","B03"));
            case "Q027-019" -> selected.equals(switch (turn) { case 0, 4 -> List.of(); case 1 -> List.of("B05","B06"); case 2 -> List.of("B06"); default -> List.of("B05"); });
            case "Q027-020" -> turn == 0 && selected.equals(List.of("B05","B06"));
            default -> throw new IllegalArgumentException("Unknown approved scenario " + id);
        };
    }
    private static boolean cardBeforeType(List<String> selected) {
        int firstType = selected.size();
        for (int index = 0; index < selected.size(); index++) if (selected.get(index).startsWith("B")) { firstType = index; break; }
        return selected.indexOf("G01") < firstType && selected.indexOf("G02") < firstType;
    }
    private List<String> hardViolations(String id, int turn, List<String> selected, StoneChatTurnEntity completed) {
        List<String> violations = new ArrayList<>(); String prose = completed.getText().toLowerCase(Locale.ROOT);
        if (Set.of("Q027-007","Q027-008","Q027-011").contains(id)) {
            if (!cardBeforeType(selected)) violations.add("CARD_PRIORITY_ORDER");
            if (completed.getExactSearchCriteria() != null && (!completed.getExactSearchCriteria().getStoneTypes().isEmpty() || !completed.getExactSearchCriteria().getStoneSizes().isEmpty())) violations.add("UNASKED_EXACT_FILTER");
        }
        if (Set.of("Q027-001","Q027-002","Q027-003").contains(id)) {
            int count = id.equals("Q027-001") ? 8 : id.equals("Q027-002") ? 7 : 2;
            if (!prose.contains("found " + count + " matching") && !prose.contains("found " + count + " available")) violations.add("EXACT_COUNT_FRAMING");
        }
        if (selected.size() > 5 || selected.stream().distinct().count() != selected.size()) violations.add("PRESENTATION_BOUND_OR_DUPLICATES");
        if (prose.matches("(?s).*\\b(according to|encyclopedia|source|document|biography)\\b.*") || prose.contains("http://") || prose.contains("https://")) violations.add("VISIBLE_PROVENANCE");
        for (String key : selected) {
            if (key.startsWith("UNOWNED:")) { violations.add("INVENTED_ID"); continue; }
            List<String> statuses = fixture.jdbcTemplate().queryForList("SELECT adoption_status FROM stone WHERE id=?", String.class, fixture.ids().get(key));
            if (!statuses.equals(List.of("AVAILABLE"))) violations.add("NOT_AVAILABLE:" + key);
            if (id.equals("Q027-007") || id.equals("Q027-008") || id.equals("Q027-009") || id.equals("Q027-010") || id.equals("Q027-011") || (Set.of("Q027-014","Q027-015").contains(id) && turn == 0) || (id.equals("Q027-014") && turn == 1)) {
                if (Set.of("B10","G03","O01").contains(key)) violations.add("CARD_SHELF_CONTRADICTION:" + key);
            }
            if (Set.of("Q027-001","Q027-002","Q027-003","Q027-009","Q027-010","Q027-012","Q027-014","Q027-016","Q027-017","Q027-018","Q027-020").contains(id)
                    || (id.equals("Q027-015") && turn == 0) || (id.equals("Q027-019") && turn > 0)) {
                if (!fixture.card(key).path("type").asString().equals("BASALT")) violations.add("WRONG_TYPE:" + key);
                boolean large = (id.equals("Q027-014") && turn == 1) || (id.equals("Q027-019") && turn == 4);
                boolean small = !id.equals("Q027-001") && !large;
                if ((large && !fixture.card(key).path("size").asString().equals("LARGE")) || (small && !fixture.card(key).path("size").asString().equals("SMALL"))) violations.add("WRONG_SIZE:" + key);
            }
            if (Set.of("Q027-003","Q027-016","Q027-018","Q027-020").contains(id) || (id.equals("Q027-019") && turn > 0)) {
                if (!Set.of("B05","B06").contains(key)) violations.add("WRONG_DATE_OR_REFERENCE:" + key);
            }
        }
        if (Set.of("Q027-001","Q027-002","Q027-003","Q027-004","Q027-005","Q027-006","Q027-016","Q027-017","Q027-018").contains(id)
                || (id.equals("Q027-019") && turn > 0)) if (!quality(id, turn, selected)) violations.add("EXACT_OR_REFERENCE_ORACLE");
        if (id.equals("Q027-005") && completed.getText().chars().filter(character -> character == '?').count() < 2) violations.add("MISSING_TWO_CLARIFICATION_QUESTIONS");
        if (id.equals("Q027-006") && !prose.contains("year")) violations.add("MISSING_YEAR_CLARIFICATION");
        if (Set.of("Q027-004","Q027-005","Q027-006","Q027-013","Q027-018").contains(id) && stages.stream().anyMatch(stage -> "GENERATION".equals(stage.get("stage")))) violations.add("UNEXPECTED_GENERATION");
        if (Set.of("Q027-017","Q027-018").contains(id) && !raceDone) violations.add("REQUIRED_RACE_NOT_EXERCISED");
        long interpretationCalls = stages.stream().filter(stage -> "INTERPRETATION".equals(stage.get("stage"))).count();
        if (interpretationCalls > 2) violations.add("INTERPRETATION_CAP");
        if (prose.matches("(?s).*\\b(blue|turquoise)\\b.*") && !prose.matches("(?s).*\\b(no|not|unknown|unconfirmed|cannot|missing|unfulfilled)\\b.*")) violations.add("POSSIBLE_FALSE_UNSUPPORTED_COLOR_REQUIRES_REVIEW");
        return violations;
    }
    private void writeEvidence(String status) throws Exception {
        lastStatus = status; Map<String, Object> root = new LinkedHashMap<>(); root.put("setupAttempts", setupAttempts); if (retainedOriginal != null) { root.put("priorOriginalEvidence", retainedOriginal); root.put("correctionDiagnostics", correctionDiagnostics); } root.put("runId", runId); root.put("status", status); root.put("fixtureRevision", fixture.version());
        root.put("calendar", fixture.resource().path("calendar")); root.put("accountingClock", "advancing actual UTC"); root.put("mapping", fixture.ids());
        root.put("profiles", Map.of("chatModel", "qwen2.5:3b", "chatDigest", "357c53fb659c5076de1d65ccb0b397446227b71a42be9d1603d46168015c9e4b", "embeddingModel", StoneDocumentEmbeddingInputEntity.MODEL, "embeddingProfile", StoneSearchQualificationFixture.PROFILE));
        Map<String, Object> settings = new LinkedHashMap<>(); settings.put("concurrency", 1); settings.put("dailyBudget", dailyBudget); settings.put("context", 8192); settings.put("checkingOutput", 512); settings.put("generationOutput", 1024);
        settings.put("callDeadline", "PT50S"); settings.put("turnDeadline", "PT120S"); settings.put("finalizationReserve", "PT5S"); settings.put("candidateLimit", 10); settings.put("passageLimit", 10); settings.put("cardThreshold", 0.5); settings.put("passageThreshold", 0.5);
        settings.put("evidenceCap", 20); settings.put("generationCap", 4); settings.put("queryEmbeddingCap", 2); settings.put("refreshCap", 1); root.put("settings", settings);
        root.put("compatibleFixtureVectors", fixture.vectorCache()); root.put("cases", cases); root.put("accounting", fixture.accounting()); root.put("humanReview", "DEFERRED_OUT_OF_SCOPE"); root.put("fixedDenominators", Map.of("scenarios", 20, "turns", 28, "semantic", 9, "answers", 12));
        long semanticPass = cases.stream().filter(scenario -> cohort(jsonMapper.valueToTree(scenario), "R") && Boolean.TRUE.equals(scenario.get("candidateOracle"))).count(); root.put("originalSemanticPass", semanticPass);
        root.put("finalQualification", "NOT_PASSED: full original functional evidence and independent autonomous assessment required");
        Files.writeString(results, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
        StringBuilder readable = new StringBuilder("# Feature 0027 Original Qualification Evidence\n\nStatus: " + status + ". User review: DEFERRED_OUT_OF_SCOPE; current assessment: autonomous.\n\nRun: " + runId + "; fixture: " + fixture.version() + ".\nOriginal semantic outcomes: " + semanticPass + "/9; answer denominator: 12; required turns: 28.\nNo favorable repetition replaces the original result. No performance measurement.\n\n");
        for (Map<String, Object> scenario : cases) {
            readable.append("## ").append(scenario.get("id")).append("\n\n");
            @SuppressWarnings("unchecked") var turns = (List<Map<String, Object>>) scenario.get("turns");
            for (Map<String, Object> turn : turns) {
                readable.append("User: ").append(turn.get("input")).append("\n\n");
                if (turn.get("answer") instanceof StoneChatMessageResponse answer) readable.append("Assistant:\n\n").append(answer.text()).append("\n\nLinks: ").append(turn.get("selectedKeys")).append(".\n\n");
                else readable.append("Outcome: ").append(turn.get("outcome")).append("; ").append(turn.get("exception")).append(".\n\n");
                readable.append("Automatic violations: ").append(turn.get("hardViolations")).append(". Answer quality: ").append(turn.get("answerQuality")).append(".\n\n");
            }
        }
        readable.append("## Autonomous assessment rubric\n\nReview permitted content and card precedence, relevance, individual-property truthfulness, unmatched/alternative labels, brief introduction and per-stone coverage, and no visible provenance. Agent verdicts and independent reasons are recorded without user input; deferred user review is not a completion gate. Templates retain their non-quota failure classification. Free-text truthfulness requires independent assessment, not the production checker alone.\n");
        Files.writeString(report, (retainedReport == null ? "" : retainedReport + "\n# Approved Correction Continuation (Original Failure Retained)\n\n") + readable);
    }
    private static Set<String> diagnosticIds(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Explicit failed/ambiguous case IDs and hypotheses required");
        Set<String> ids = Set.of(value.split(","));
        for (String id : ids) if (!id.matches("Q027-(00[1-9]|01[0-9]|020)")) throw new IllegalArgumentException("Unapproved case " + id);
        return ids;
    }
    @Test void diagnoseApprovedCases() throws Exception {
        diagnosticMode = true; Set<String> selected = diagnosticIds(System.getenv("STONE_QUALIFICATION_DIAGNOSTIC_CASES"));
        String hypothesis = required("STONE_QUALIFICATION_DIAGNOSTIC_HYPOTHESIS");
        int repetitions = Integer.parseInt(required("STONE_QUALIFICATION_DIAGNOSTIC_REPETITIONS"));
        if (repetitions < 1 || repetitions > 2) throw new IllegalArgumentException("At most two diagnostic repetitions");
        ObjectNode original = (ObjectNode) jsonMapper.readTree(Files.readString(results));
        if (!original.path("status").asString().startsWith("BASELINE_COLLECTED")) throw new IllegalStateException("Complete original baseline required before diagnosis");
        fixture = new StoneSearchQualificationFixture(); runId = UUID.randomUUID();
        if (!fixture.version().equals(original.path("fixtureRevision").asString())) throw new IllegalStateException("Fixture revision changed");
        for (String id : selected) {
            JsonNode baseline = null;
            for (JsonNode value : original.path("cases")) if (value.path("id").asString().equals(id)) baseline = value;
            if (baseline == null) throw new IllegalStateException("Missing original case");
            if (baseline.path("budgetExhausted").asBoolean()) throw new IllegalStateException("Budget-interrupted verification is incomplete, not a quality diagnostic");
            boolean failed = cohort(baseline, "R") && !baseline.path("candidateOracle").asBoolean();
            for (JsonNode turn : baseline.path("turns")) {
                failed |= turn.path("answerQuality").asString().startsWith("FAIL");
                for (JsonNode violation : turn.path("hardViolations")) if (!violation.asString().equals("EXPECTED_KNOWLEDGE_GENERATION_FAILURE")) failed = true;
            }
            failed |= Set.of("FAIL", "AMBIGUOUS").contains(baseline.path("answerReviewVerdict").asString());
            if (!failed) throw new IllegalArgumentException("Successful/unreviewed case cannot be repeated: " + id);
            int previous = 0;
            for (JsonNode attempt : original.path("diagnostics")) if (attempt.path("id").asString().equals(id)) previous++;
            if (previous + repetitions > 2) throw new IllegalArgumentException("Diagnostic bound exhausted: " + id);
        }
        try {
            fixture.connect(); configure(); fixture.seed();
            fixture.restoreCompatibleVectors(jsonMapper.convertValue(original.path("compatibleFixtureVectors"), new TypeReference<Map<String, String>>() {}));
            var diagnostics = original.has("diagnostics") ? original.withArray("diagnostics") : original.putArray("diagnostics");
            for (int repetition = 0; repetition < repetitions; repetition++) for (JsonNode scenario : fixture.resource().path("scenarios")) if (selected.contains(scenario.path("id").asString())) {
                ObjectNode placeholder = diagnostics.addObject().put("id", scenario.path("id").asString()).put("runId", runId.toString()).put("hypothesis", hypothesis).put("status", "IN_PROGRESS").put("dailyBudget", dailyBudget);
                Files.writeString(results, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original) + "\n");
                var attempt = collectScenario(scenario); placeholder.set("observed", jsonMapper.valueToTree(attempt)); placeholder.set("mapping", jsonMapper.valueToTree(fixture.ids()));
                placeholder.put("status", Boolean.TRUE.equals(attempt.get("budgetExhausted")) ? "INCOMPLETE_BUDGET_EXHAUSTED" : Boolean.TRUE.equals(attempt.get("controlOutage")) ? "INCOMPLETE_CONTROL_OUTAGE" : "COLLECTED");
                original.set("diagnosticAccounting", jsonMapper.valueToTree(fixture.accounting()));
                Files.writeString(results, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(original) + "\n");
                Files.writeString(report, "\n## Diagnostic " + scenario.path("id").asString() + "\n\nHypothesis: " + hypothesis + ". Original scores are unchanged.\n\n" + jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(attempt) + "\n", StandardOpenOption.APPEND);
                if (Boolean.TRUE.equals(attempt.get("budgetExhausted")) || Boolean.TRUE.equals(attempt.get("controlOutage"))) return;
            }
        } finally {
            if (serialization != null) serialization.close();
            fixture.close();
        }
    }
    private String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " missing"); return value; }
}
