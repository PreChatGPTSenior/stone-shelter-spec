package lab.stoneshelter;

import java.util.Locale;
import lab.stoneshelter.mappers.dtos.StoneRagGenerationRequestToStoneRagGroundingRequestMapper;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import java.util.function.Function;
import java.util.function.Consumer;
import tools.jackson.core.JacksonException;

import java.nio.charset.StandardCharsets;
import lab.stoneshelter.services.StoneRagGroundingService;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.entities.StoneRagGeneratedAnswerEntity;
import lab.stoneshelter.entities.StoneRagExecutionEntity;
import lab.stoneshelter.mappers.dtos.StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper;
import lab.stoneshelter.services.OllamaRagRoutingProvider;
import lab.stoneshelter.services.OllamaRagAnswerProvider;
import lab.stoneshelter.services.OllamaRagGroundingProvider;
import lab.stoneshelter.services.StoneRagDecisionValidationService;
import lab.stoneshelter.services.StoneRagAnswerValidationService;
import lab.stoneshelter.services.StoneRagSettings;
import lab.stoneshelter.services.StoneSemanticSearchValidationService;
import lab.stoneshelter.services.StoneExactSearchValidationService;
import lab.stoneshelter.mappers.dtos.StoneRagDecisionToStoneSemanticSearchDecisionMapper;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import lab.stoneshelter.shared.StoneRagCandidateRequest;
import lab.stoneshelter.shared.StoneRagSourceRequest;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import java.util.UUID;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.StoneExactSearchSettings;
import lab.stoneshelter.services.StoneStructuredModelService;
import lab.stoneshelter.services.OllamaSemanticSearchProvider;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixed basic oracle through the final production routing/generation/checking providers. */
class StoneRagAnswerQualificationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff").asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static ModelInvocationAdmissionService admission;
    private static JdbcTemplate jdbcTemplate;
    private static JsonNode fixture;
    private final StoneStructuredModelService guard = new StoneStructuredModelService(new OllamaChatRepository(), admission,
            new StoneExactSearchSettings(new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", System.getenv("CHAT_MODEL_OLLAMA_BASE_URL"))), Clock.systemUTC());
    @BeforeAll
    static void prepareIsolatedLedger() throws Exception {
        assertThat(System.getenv("CHAT_MODEL_OLLAMA_BASE_URL")).isNotBlank();
        assertThat(System.getenv("STONE_DOCUMENT_OLLAMA_BASE_URL")).isNotBlank();
        try (var resource = StoneRagAnswerQualificationTest.class.getResourceAsStream("/rag-answer-qualification.json")) {
            assertThat(resource).isNotNull();
            fixture = JSON.readTree(resource.readAllBytes());
        }
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        springLiquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(Transactions.class, ModelTokenBudgetRepository.class, ModelTokenBudgetTransactionService.class);
        context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        admission = new ModelInvocationAdmissionService(context.getBean(ModelTokenBudgetTransactionService.class),
                new ModelInvocationSettings(new MockEnvironment()), Clock.systemUTC());
    }

    @AfterAll
    static void closeIsolatedInfrastructure() {
        if (jdbcTemplate != null) {
            System.out.println("RAG prototype ledger: " + jdbcTemplate.queryForList(
                    "SELECT budget_day,charged_tokens FROM model_token_budget_day ORDER BY budget_day"));
            System.out.println("RAG prototype reservations: " + jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM model_token_reservation", Long.class));
        }
        if (context != null) context.close();
        DATABASE.stop();
    }


    @Test void selectedModelRoutesSelectionKnowledgeAndMixedWithoutCareWish() {
        var provider = routing();
        for (JsonNode example : fixture.path("routes")) {
            StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage(example.path("input").asString());
            JsonNode result = invokeProduction(repair -> provider.invoke(turn, List.of(), Instant.now().plusSeconds(115), repair), value -> {
                new StoneRagDecisionValidationService(new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()),
                        new StoneRagDecisionToStoneSemanticSearchDecisionMapper()).validate(JSON.writeValueAsString(value));
                        assertThat(value.propertyNames()).containsExactlyInAnyOrder("mode", "criteria", "resetSearch", "reference", "questions", "reason", "wishes", "topic");
                        assertThat(value.path("mode").asString()).isEqualTo(example.path("mode").asString());
                        assertThat(value.path("reference").isNull()).isTrue();
                        if (example.path("mode").asString().equals("KNOWLEDGE")) {
                            assertThat(value.path("topic").asString()).isNotBlank();
                            assertThat(value.at("/criteria/stoneTypes/0").asString()).isEqualTo(example.path("topicType").asString());
                        } else {
                            new StoneSemanticSearchValidationService(new StoneExactSearchValidationService())
                                    .validate(JSON.writeValueAsString(selectionDecision(value)));
                            assertThat(value.at("/criteria/stoneTypes")).isEqualTo(example.path("types"));
                            assertThat(value.at("/criteria/stoneSizes")).isEqualTo(example.path("sizes"));
                            List<String> keys = new ArrayList<>();
                            for (String key : List.of("color", "use")) if (!value.at("/wishes/" + key).isNull()) keys.add(key);
                            for (JsonNode wish : value.at("/wishes/other")) keys.add(wish.path("key").asString());
                            assertThat(keys).containsExactlyInAnyOrderElementsOf(JSON.convertValue(example.path("wishKeys"), List.class));
                            if (example.path("topicRequired").asBoolean()) assertThat(value.path("topic").asString()).isNotBlank();
                        }
                    });
            System.out.println("RAG prototype route: " + result);
        }
    }

    @Test void selectedModelGeneratesPerStoneAndKnowledgeSourceFreeAnswers() {
        for (String mode : List.of("selection", "knowledge")) {
            JsonNode supplied = fixture.path(mode);
            StoneRagGenerationRequest request = generationRequest(supplied);
            var provider = new OllamaRagAnswerProvider(guard, ragSettings());
            JsonNode draft = invokeProduction(repair -> provider.invoke(request, repair), value -> {
                new StoneRagAnswerValidationService().validate(JSON.writeValueAsString(value), request);
                        assertThat(value.propertyNames()).containsExactlyInAnyOrder("introduction", "stones", "knowledge");
                        assertThat(value.path("stones").size()).isEqualTo(supplied.path("candidates").size());
                        if (mode.equals("selection")) {
                            assertThat(value.path("introduction").asString()).isNotBlank(); assertThat(value.path("knowledge").isNull()).isTrue();
                            for (int index = 0; index < supplied.path("candidates").size(); index++) {
                                var explanation = value.path("stones").get(index);
                                assertThat(explanation.path("id").asLong()).isEqualTo(supplied.path("candidates").get(index).path("id").asLong());
                                String text = explanation.path("explanation").asString().toLowerCase(Locale.ROOT);
                                assertThat(text).contains("shelf").containsPattern("red|colou?r"); assertThat(text).containsPattern("unconfirmed|not confirmed|cannot confirm|not verified|unknown|not guaranteed");
                            }
                        } else assertThat(value.path("knowledge").asString().toLowerCase(Locale.ROOT)).isNotBlank().containsPattern("dry|cloth|chemical").doesNotContain("stones=[]");
                        assertThat(JSON.writeValueAsString(value).toLowerCase(Locale.ROOT))
                                .doesNotContain("encyclopedia", "according to", "source", "document", "http://", "https://", "type-1", "card:");
                    });
            System.out.println("RAG prototype draft " + mode + ": " + draft);
        }
    }

    @Test void selectedModelRejectsIndividualInventionCardConflictAndVisibleAttribution() {
        for (JsonNode example : fixture.path("checks")) {
            var provider = new OllamaRagGroundingProvider(guard);
            var facts = generationRequest(fixture.path("selection"));
            MockEnvironment environment = new MockEnvironment();
            var service = new StoneRagGroundingService((request, repair) -> {
                var response = provider.invoke(request, repair);
                System.out.println("RAG production grounding usage: input=" + response.inputTokens() + ", output=" + response.outputTokens());
                System.out.println("RAG production grounding raw: " + response.text());
                return response;
            }, provider, new StoneRagGenerationRequestToStoneRagGroundingRequestMapper(),
                    new StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper(),
                    new StoneSemanticSearchSettings(environment, new StoneCardSearchSettings(environment)), Clock.systemUTC());
            StoneRagExecutionEntity execution = new StoneRagExecutionEntity();
            execution.setDeadline(facts.getDeadline()); execution.setModelDeadline(facts.getDeadline());
            long reservations = jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class);
            var result = service.prepare(facts, new StoneRagGeneratedAnswerEntity("", List.of(
                    new StoneRagGeneratedAnswerEntity.Explanation(201L, example.path("text").asString())), null), execution);
            assertThat(result.valid()).isEqualTo(example.path("valid").asBoolean());
            if (example.path("text").asString().startsWith("According to")) {
                assertThat(execution.getEvidenceCalls()).isZero();
                assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class)).isEqualTo(reservations);
            } else assertThat(execution.getEvidenceCalls()).isEqualTo(1);
            System.out.println("RAG final grounding: " + result);
        }
    }

    @Test void fullCurrentInputAndBothOutputAllocationsFitButRequiredOverflowFailsBeforeAdmission() {
        var provider = new OllamaRagAnswerProvider(guard, ragSettings());
        var request = generationRequest(fixture.path("knowledge")); request.setQuestion("\u9fff".repeat(2000)); request.setSources(List.of());
        String system = provider.system(request);
        assertThat(provider.fits(request)).isTrue();
        assertThat(guard.fits(routing().system(List.of(), null), "\u9fff".repeat(2000), 512, 200)).isTrue();
        long before = jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class);
        assertThatThrownBy(() -> guard.invoke(system + "mandatory-facts".repeat(1000), "question", JSON.convertValue(checkingSchema(), Map.class),
                Instant.now().plusSeconds(50), 1024)).isInstanceOf(StoneSemanticSearchLimitExceededException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class)).isEqualTo(before);
        StoneModelInvocationEntity invocation = new StoneModelInvocationEntity(); invocation.setSystem(system); invocation.setMessage(request.getQuestion());
        invocation.setHistory(List.of()); invocation.setIncludeExactSearchExample(false);
        System.out.println("RAG production generation maximum-input bound=" + (OllamaChatRepository.promptUpperBound(invocation) + 1024 + 200));
    }

    private StoneRagSettings ragSettings() { return new StoneRagSettings(new MockEnvironment(), new StoneExactSearchSettings(new MockEnvironment()
            .withProperty("CHAT_MODEL_OLLAMA_BASE_URL", System.getenv("CHAT_MODEL_OLLAMA_BASE_URL")))); }
    private OllamaRagRoutingProvider routing() { return new OllamaRagRoutingProvider(guard,
            new OllamaSemanticSearchProvider(guard, Clock.systemUTC(), new StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper()),
            Clock.systemUTC(), new StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper()); }
    private JsonNode invokeProduction(Function<String, StoneModelResponseEntity> call,
            Consumer<JsonNode> validate) {
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = call.apply(attempt == 0 ? null : "Return only exact schema/known IDs/order, complete explanations and unconfirmed wishes; care is topic, never a new selection wish.");
            System.out.println("RAG production usage: input=" + response.inputTokens() + ", output=" + response.outputTokens());
            JsonNode result = JSON.readTree(response.text()); System.out.println("RAG production raw: " + result);
            try { validate.accept(result); return result; }
            catch (AssertionError | RuntimeException exception) { if (attempt == 1) throw exception; }
        }
        throw new AssertionError("No checked final provider result");
    }
    private StoneRagGenerationRequest generationRequest(JsonNode supplied) {
        StoneRagGenerationRequest request = new StoneRagGenerationRequest(); request.setMode(StoneRagMode.valueOf(supplied.path("mode").asString()));
        request.setQuestion(supplied.path("question").asString()); request.setDeadline(Instant.now().plusSeconds(115));
        List<StoneRagCandidateRequest> candidates = new ArrayList<>();
        for (JsonNode suppliedCandidate : supplied.path("candidates")) {
            StoneRagCandidateRequest candidate = new StoneRagCandidateRequest(); candidate.setId(suppliedCandidate.path("id").asLong());
            candidate.setName(suppliedCandidate.path("name").asString()); candidate.setStoneType(StoneType.valueOf(suppliedCandidate.path("type").asString()));
            candidate.setStoneSize(StoneSize.valueOf(suppliedCandidate.path("size").asString()));
            if (suppliedCandidate.has("biography")) candidate.setBiography(suppliedCandidate.path("biography").asString());
            List<StoneSemanticWishRequest> matched = new ArrayList<>(), unmatched = new ArrayList<>();
            for (JsonNode wish : suppliedCandidate.path("matched")) matched.add(new StoneSemanticWishRequest("use", wish.asString()));
            for (JsonNode wish : suppliedCandidate.path("unmatched")) unmatched.add(new StoneSemanticWishRequest("color", wish.asString()));
            candidate.setMatched(matched); candidate.setUnmatched(unmatched); candidates.add(candidate);
        }
        request.setCandidates(candidates);
        request.setSources(List.of(new StoneRagSourceRequest("type-1", StoneSemanticSupportSource.TYPE, StoneType.BASALT, supplied.path("typeContext").asString())));
        return request;
    }

    private JsonNode invoke(String system, String input, JsonNode schema, int output, Instant deadline, Consumer<JsonNode> validate) {
        String repair = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = guard.invoke(system + repair, input, JSON.convertValue(schema, Map.class), deadline, output);
            System.out.println("RAG prototype usage: input=" + response.inputTokens() + ", output=" + response.outputTokens());
            JsonNode result = JSON.readTree(response.text()); System.out.println("RAG prototype raw: " + result);
            try { validate.accept(result); return result; }
            catch (AssertionError | JacksonException exception) {
                if (attempt == 1) throw exception;
                repair = " Repair: follow mode/schema exactly; retain all IDs/order, missing individual properties are unconfirmed, and expose no sources.";
            }
        }
        throw new AssertionError("No checked prototype result");
    }

    private JsonNode selectionDecision(JsonNode value) {
        ObjectNode selection = ((ObjectNode) value).deepCopy(); selection.remove("mode"); selection.remove("topic"); selection.put("action", "SEARCH"); return selection;
    }

    private String generationSystem(JsonNode supplied) { return fixture.path("generationInstructions").asString() + " Facts: " + JSON.writeValueAsString(supplied); }
    private JsonNode generationSchema(JsonNode supplied) {
        ObjectNode schema = (ObjectNode) JSON.readTree("""
                {"type":"object","additionalProperties":false,"required":["introduction","stones","knowledge"],"properties":{
                "introduction":{"type":"string"},"stones":{"type":"array","items":{"type":"object","additionalProperties":false,
                "required":["id","explanation"],"properties":{"id":{"type":"integer"},"explanation":{"type":"string"}}}},"knowledge":{"type":["string","null"]}}}
                """);
        ObjectNode stones = (ObjectNode) schema.at("/properties/stones"); stones.put("minItems", supplied.path("candidates").size()); stones.put("maxItems", supplied.path("candidates").size());
        if (!supplied.path("candidates").isEmpty()) {
            var ids = ((ObjectNode) stones.at("/items/properties/id")).putArray("enum"); for (JsonNode candidate : supplied.path("candidates")) ids.add(candidate.path("id").asLong());
            ((ObjectNode) schema.at("/properties/knowledge")).put("type", "null");
        } else ((ObjectNode) schema.at("/properties/knowledge")).put("type", "string");
        return schema;
    }
    private JsonNode checkingSchema() { return JSON.readTree("""
            {"type":"object","additionalProperties":false,"required":["valid","reason"],"properties":{"valid":{"type":"boolean"},"reason":{"type":"string"}}}
            """); }
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
