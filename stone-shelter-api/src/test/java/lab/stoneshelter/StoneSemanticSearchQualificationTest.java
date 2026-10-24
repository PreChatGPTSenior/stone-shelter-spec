package lab.stoneshelter;

import java.nio.charset.StandardCharsets;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.shared.StoneSemanticEvidenceSourceRequest;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import lab.stoneshelter.services.StoneStructuredModelService;
import lab.stoneshelter.services.OllamaSemanticSearchProvider;
import lab.stoneshelter.services.OllamaSemanticEvidenceProvider;
import lab.stoneshelter.services.StoneSemanticSearchValidationService;
import lab.stoneshelter.services.StoneExactSearchValidationService;
import lab.stoneshelter.services.StoneSemanticEvidenceService;
import lab.stoneshelter.services.StoneSemanticEvidenceValidationService;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import java.util.UUID;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.mappers.entities.StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.services.StoneExactSearchSettings;
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

/** Small fixed prototype oracle exercised through final production providers. */
class StoneSemanticSearchQualificationTest {
    private static final int CONTEXT = 8192;
    private static final int OUTPUT = 512;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static final String SOURCE_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["judgements"],"properties":{
              "judgements":{"type":"array","items":{"type":"object","additionalProperties":false,
                "required":["key","sourceId","verdict","quote"],"properties":{
                  "key":{"type":"string"},"sourceId":{"type":"string"},
                  "verdict":{"type":"string","enum":["SUPPORTED","UNKNOWN","CONTRADICTED"]},
                  "quote":{"type":["string","null"]}}}}}}
            """;
    private static AnnotationConfigApplicationContext context;
    private static ModelInvocationAdmissionService admission;
    private static JdbcTemplate jdbcTemplate;
    private static JsonNode fixture;
    private final OllamaChatRepository repository = new OllamaChatRepository();
    private StoneSemanticSearchExecutionEntity qualificationExecution;
    private StoneStructuredModelService guard() {
        return new StoneStructuredModelService(repository, admission, new StoneExactSearchSettings(new MockEnvironment()
                .withProperty("CHAT_MODEL_OLLAMA_BASE_URL", System.getenv("CHAT_MODEL_OLLAMA_BASE_URL"))), Clock.systemUTC());
    }
    private OllamaSemanticSearchProvider provider() {
        return new OllamaSemanticSearchProvider(guard(), Clock.systemUTC(), new StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper());
    }

    @BeforeAll
    static void prepareIsolatedLedger() throws Exception {
        assertThat(System.getenv("CHAT_MODEL_OLLAMA_BASE_URL")).isNotBlank();
        assertThat(System.getenv("STONE_DOCUMENT_OLLAMA_BASE_URL")).isNotBlank();
        try (var resource = StoneSemanticSearchQualificationTest.class.getResourceAsStream("/semantic-search-qualification.json")) {
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
            System.out.println("SEMANTIC prototype ledger: " + jdbcTemplate.queryForList(
                    "SELECT budget_day,charged_tokens FROM model_token_budget_day ORDER BY budget_day"));
            System.out.println("SEMANTIC prototype reservations: " + jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM model_token_reservation", Long.class));
        }
        if (context != null) context.close();
        DATABASE.stop();
    }

    @Test
    void installedModelInterpretsSemanticMixedAndClarificationInputs() throws Exception {
        for (JsonNode example : fixture.path("interpretations")) {
            StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage(example.path("input").asString());
            Instant deadline = Instant.now().plusSeconds(120);
            String output = provider().invoke(turn, List.of(), deadline, null).text();
            System.out.println("SEMANTIC production raw interpretation: " + output);
            try { new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()).validate(output); }
            catch (InvalidStoneSemanticSearchOutputException exception) {
                output = provider().invoke(turn, List.of(), deadline, "Return the exact schema and all explicit wishes. Missing color/use are JSON null; never invent preferences or IDs.").text();
                System.out.println("SEMANTIC production raw interpretation repair: " + output);
                new StoneSemanticSearchValidationService(new StoneExactSearchValidationService()).validate(output);
            }
            JsonNode result = JSON.readTree(output);
            validateInterpretation(result, example);
            System.out.println("SEMANTIC production interpretation: " + result);
        }
    }

    @Test
    void installedModelDistinguishesPartialTypeSupportContradictionAndUnknown() throws Exception {
        for (JsonNode example : fixture.path("evidence")) {
            Instant deadline = Instant.now().plusSeconds(120);
            qualificationExecution = new StoneSemanticSearchExecutionEntity();
            qualificationExecution.setDeadline(deadline); qualificationExecution.setModelDeadline(deadline);
            JsonNode card = classifySource(example, "card:" + example.at("/candidate/id").asLong(),
                    currentCardText(example), "CARD", deadline);
            Map<String, JsonNode> sources = new java.util.LinkedHashMap<>();
            for (JsonNode source : example.at("/candidate/sources")) {
                sources.put(source.path("id").asString(), classifySource(example, source.path("id").asString(),
                        source.path("text").asString(), "TYPE", deadline));
            }
            ObjectNode decision = JSON.createObjectNode();
            ObjectNode candidate = decision.putArray("candidates").addObject();
            candidate.put("id", example.at("/candidate/id").asLong());
            var wishes = candidate.putArray("wishes");
            for (JsonNode supplied : example.path("wishes")) {
                String key = supplied.path("key").asString();
                JsonNode cardJudgement = judgement(card, key);
                ObjectNode wish = wishes.addObject();
                wish.put("key", key);
                wish.set("cardVerdict", cardJudgement.get("verdict"));
                wish.set("cardQuote", cardJudgement.get("quote"));
                var sourceResults = wish.putArray("sources");
                for (var source : sources.entrySet()) {
                    JsonNode sourceJudgement = judgement(source.getValue(), key);
                    ObjectNode sourceResult = sourceResults.addObject();
                    sourceResult.put("id", source.getKey());
                    sourceResult.set("verdict", sourceJudgement.get("verdict"));
                    sourceResult.set("quote", sourceJudgement.get("quote"));
                }
            }
            System.out.println("SEMANTIC production evidence " + example.path("case").asString() + ": " + decision);
            validateEvidence(decision, example);
        }
    }

    private String currentCardText(JsonNode example) {
        String rendered = example.at("/candidate/card").asString();
        var stone = new lab.stoneshelter.entities.StoneEntity();
        stone.setId(example.at("/candidate/id").asLong());
        stone.setName(rendered.substring("Name: ".length(), rendered.indexOf(". Type:")));
        stone.setStoneType(StoneType.valueOf(example.at("/candidate/type").asString()));
        stone.setStoneSize(lab.stoneshelter.enums.StoneSize.valueOf(rendered.substring(rendered.indexOf("Size: ") + 6, rendered.indexOf(". Biography:"))));
        String biography = rendered.substring(rendered.indexOf("Biography: ") + 11);
        stone.setBiography(biography.equals("none.") ? null : biography);
        var candidate = new lab.stoneshelter.entities.StoneSemanticCandidateEntity(); candidate.setStone(stone);
        return new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper().toDto(candidate).card();
    }

    private JsonNode classifySource(JsonNode example, String sourceId, String sourceText, String scope, Instant deadline) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        var production = new OllamaSemanticEvidenceProvider(guard());
        lab.stoneshelter.services.StoneSemanticEvidenceProvider diagnostic = (request, repair) -> {
            var response = production.invoke(request, repair);
            System.out.println("SEMANTIC production raw evidence " + sourceId + " repair=" + (repair != null) + ": " + response.text());
            return response;
        };
        var checks = new StoneSemanticEvidenceService(diagnostic, new StoneSemanticEvidenceValidationService(),
                new StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper(), new OllamaSemanticEvidenceProvider(guard()),
                new StoneSemanticSearchSettings(environment, new StoneCardSearchSettings(environment)), Clock.systemUTC(),
                new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper());
        List<StoneSemanticWishRequest> wishes = new ArrayList<>();
        for (JsonNode wish : example.path("wishes")) wishes.add(new StoneSemanticWishRequest(wish.path("key").asString(), wish.path("text").asString()));
        return JSON.valueToTree(checks.checkSources(List.of(new StoneSemanticEvidenceSourceRequest(sourceId,
                StoneSemanticSupportSource.valueOf(scope), StoneType.valueOf(example.at("/candidate/type").asString()), sourceText)), wishes, qualificationExecution));
    }

    private JsonNode judgement(JsonNode response, String key) {
        for (JsonNode judgement : response.path("judgements")) if (key.equals(judgement.path("key").asString())) return judgement;
        throw new AssertionError("Missing supplied wish: " + key);
    }

    @Test
    void installedCompatibleVectorCanServeBothSourceSimilaritiesWithoutReembeddingTheQuery() {
        MockEnvironment environment = new MockEnvironment().withProperty("STONE_DOCUMENT_OLLAMA_BASE_URL",
                System.getenv("STONE_DOCUMENT_OLLAMA_BASE_URL"));
        StoneDocumentEmbeddingService stoneDocumentEmbeddingService = new StoneDocumentEmbeddingService(new OllamaEmbeddingRepository(),
                new StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper(new StoneDocumentIndexSettings(environment), Clock.systemUTC()));
        stoneDocumentEmbeddingService.configureAdmission(admission, Clock.systemUTC());
        Instant deadline = Instant.now().plusSeconds(50);
        StoneDocumentEmbeddingEntity query = stoneDocumentEmbeddingService.embed(new StoneDocumentEmbeddingCriteria(
                fixture.at("/embedding/query").asString(), true, deadline, ModelAdmissionMode.CUSTOMER));
        StoneDocumentEmbeddingEntity card = stoneDocumentEmbeddingService.embed(new StoneDocumentEmbeddingCriteria(
                fixture.at("/embedding/card").asString(), false, deadline, ModelAdmissionMode.CUSTOMER));
        StoneDocumentEmbeddingEntity passage = stoneDocumentEmbeddingService.embed(new StoneDocumentEmbeddingCriteria(
                fixture.at("/embedding/passage").asString(), false, deadline, ModelAdmissionMode.CUSTOMER));
        assertThat(query.vector()).hasSize(1024);
        assertThat(java.util.Arrays.stream(query.vector()).allMatch(Double::isFinite)).isTrue();
        assertThat(card.vector()).hasSize(1024);
        assertThat(java.util.Arrays.stream(card.vector()).allMatch(Double::isFinite)).isTrue();
        assertThat(passage.vector()).hasSize(1024);
        assertThat(java.util.Arrays.stream(passage.vector()).allMatch(Double::isFinite)).isTrue();
        assertThat(cosine(query.vector(), card.vector())).isBetween(-1.0, 1.0);
        assertThat(cosine(query.vector(), passage.vector())).isBetween(-1.0, 1.0);
        System.out.println("SEMANTIC prototype compatible query reused; card=" + cosine(query.vector(), card.vector())
                + ", passage=" + cosine(query.vector(), passage.vector()));
    }

    @Test
    void completeCurrentInputFitsAndRequiredStateOrEvidenceCannotBeSilentlyTruncated() {
        StoneModelInvocationEntity invocation = invocation(provider().system(List.of(), null),
                "\u9fff".repeat(2000), provider().schema(), Instant.now().plusSeconds(50));
        int bound = OllamaChatRepository.promptUpperBound(invocation) + OUTPUT + StoneExactSearchSettings.REPAIR_PROMPT_ALLOWANCE;
        assertThat(bound).as("Complete current-input byte bound includes required initial state and repair allowance").isLessThanOrEqualTo(CONTEXT);
        StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage("shelf");
        var state = new lab.stoneshelter.entities.StoneSemanticSearchStateEntity();
        state.setExact(new lab.stoneshelter.criteria.StoneExactSearchCriteria());
        state.setWishes(List.of(new lab.stoneshelter.criteria.StoneSemanticWish("use", "required-state".repeat(1000))));
        turn.setSemanticSearchState(state);
        long reservations = jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class);
        assertThatThrownBy(() -> provider().invoke(turn, List.of(turn), Instant.now().plusSeconds(50), null))
                .isInstanceOf(lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class)).isEqualTo(reservations);
        System.out.println("SEMANTIC production maximum-input upper bound=" + bound + "; mandatory overflow fails before admission");
    }

    private JsonNode corrected(String system, String input, Map<String, Object> schema,
            java.util.function.Consumer<JsonNode> validator) throws Exception {
        return corrected(system, input, schema, validator, Instant.now().plusSeconds(120));
    }

    private JsonNode corrected(String system, String input, Map<String, Object> schema,
            java.util.function.Consumer<JsonNode> validator, Instant deadline) throws Exception {
        JsonNode result;
        try {
            result = JSON.readTree(invoke(system, input, schema, deadline).text());
            System.out.println("SEMANTIC prototype raw response: " + result);
            validator.accept(result);
            return result;
        } catch (AssertionError | tools.jackson.core.JacksonException malformed) {
            result = JSON.readTree(invoke(system + " Repair: preserve all supplied IDs/keys; use only verbatim evidence. Follow the schema and rules exactly.",
                    input, schema, deadline).text());
            System.out.println("SEMANTIC prototype raw response: " + result);
            validator.accept(result);
            return result;
        }
    }

    private StoneModelResponseEntity invoke(String system, String input, Map<String, Object> schema, Instant deadline) throws Exception {
        Instant callDeadline = Instant.now().plusSeconds(50);
        if (deadline.isBefore(callDeadline)) callDeadline = deadline;
        StoneModelInvocationEntity invocation = invocation(system, input, schema, callDeadline);
        requireCapacity(invocation);
        var lease = admission.acquire(ModelAdmissionMode.CUSTOMER, CONTEXT, invocation.getDeadline());
        CompletableFuture<StoneModelResponseEntity> result = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().name("semantic-prototype-model").start(() -> {
            try (lease) {
                repository.probe(invocation.getEndpoint());
                StoneModelResponseEntity response = repository.invoke(invocation);
                lease.settle((long) response.inputTokens() + response.outputTokens());
                System.out.println("SEMANTIC prototype usage: input=" + response.inputTokens() + ", output=" + response.outputTokens());
                result.complete(response);
            } catch (RuntimeException exception) { result.completeExceptionally(exception); }
        });
        try {
            return result.get(Math.max(1, Duration.between(Instant.now(), invocation.getDeadline()).toNanos()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException exception) {
            worker.interrupt();
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Prototype model invocation failed", exception.getCause());
        }
    }

    private StoneModelInvocationEntity invocation(String system, String input, Map<String, Object> schema, Instant deadline) {
        StoneModelInvocationEntity stoneModelInvocationEntity = new StoneModelInvocationEntity();
        stoneModelInvocationEntity.setEndpoint(System.getenv("CHAT_MODEL_OLLAMA_BASE_URL"));
        stoneModelInvocationEntity.setSystem(system);
        stoneModelInvocationEntity.setMessage(input);
        stoneModelInvocationEntity.setHistory(List.of());
        stoneModelInvocationEntity.setIncludeExactSearchExample(false);
        stoneModelInvocationEntity.setSchema(schema);
        stoneModelInvocationEntity.setContextTokens(CONTEXT);
        stoneModelInvocationEntity.setOutputTokens(OUTPUT);
        stoneModelInvocationEntity.setDeadline(deadline);
        return stoneModelInvocationEntity;
    }

    private void requireCapacity(StoneModelInvocationEntity invocation) {
        if (OllamaChatRepository.promptUpperBound(invocation) + OUTPUT > CONTEXT) {
            throw new IllegalArgumentException("Required prototype input exceeds the qualified context");
        }
    }

    private Map<String, Object> interpretationSchema(String input) {
        ObjectNode schema = (ObjectNode) JSON.convertValue(new StoneExactSearchSettings(
                new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", System.getenv("CHAT_MODEL_OLLAMA_BASE_URL")))
                .schemaFor(input, false), ObjectNode.class);
        ((ObjectNode) schema.get("properties")).set("wishes", JSON.readTree("""
                {"type":"object","additionalProperties":false,"required":["color","use","other"],"properties":{
                  "color":{"type":["string","null"]},"use":{"type":["string","null"]},
                  "other":{"type":"array","items":{"type":"object","additionalProperties":false,
                    "required":["key","text"],"properties":{"key":{"type":"string"},"text":{"type":"string"}}}}}}
                """));
        ((tools.jackson.databind.node.ArrayNode) schema.get("required")).add("wishes");
        return JSON.convertValue(schema, Map.class);
    }

    private void validateInterpretation(JsonNode decision, JsonNode example) {
        assertThat(decision.propertyNames()).containsExactlyInAnyOrder("action", "criteria", "resetSearch", "reference", "questions", "reason", "wishes");
        assertThat(decision.path("action").asString()).isEqualTo(example.path("action").asString());
        assertThat(decision.at("/criteria/stoneTypes")).isEqualTo(example.path("types"));
        assertThat(decision.at("/criteria/stoneSizes")).isEqualTo(example.path("sizes"));
        assertThat(decision.at("/criteria/availability")).isEqualTo(JSON.readTree("[\"AVAILABLE\"]"));
        assertThat(decision.path("resetSearch").asBoolean()).isFalse();
        assertThat(decision.path("reference").isNull()).isTrue();
        List<String> keys = new ArrayList<>();
        assertThat(decision.path("wishes").propertyNames()).containsExactlyInAnyOrder("color", "use", "other");
        for (String key : List.of("color", "use")) {
            JsonNode value = decision.path("wishes").path(key);
            if (!value.isNull()) {
                assertThat(value.isString()).isTrue();
                assertThat(value.asString()).isNotBlank().isNotIn("null", "none");
                keys.add(key);
            }
        }
        for (JsonNode wish : decision.at("/wishes/other")) {
            keys.add(wish.path("key").asString());
            assertThat(wish.path("text").asString()).isNotBlank().isNotIn("null", "none");
        }
        assertThat(keys).containsExactlyInAnyOrderElementsOf(JSON.convertValue(example.path("wishKeys"), List.class));
        if ("CLARIFY".equals(decision.path("action").asString())) assertThat(decision.path("questions").size()).isGreaterThanOrEqualTo(2);
    }

    private void validateEvidence(JsonNode decision, JsonNode example) {
        assertThat(decision.propertyNames()).containsExactly("candidates");
        assertThat(decision.path("candidates").size()).isEqualTo(1);
        JsonNode candidate = decision.at("/candidates/0");
        assertThat(candidate.propertyNames()).containsExactlyInAnyOrder("id", "wishes");
        assertThat(candidate.path("id").asLong()).isEqualTo(example.at("/candidate/id").asLong());
        List<String> wishes = new ArrayList<>();
        List<String> supportedSources = new ArrayList<>();
        for (JsonNode wish : candidate.path("wishes")) {
            assertThat(wish.propertyNames()).containsExactlyInAnyOrder("key", "cardVerdict", "cardQuote", "sources");
            String key = wish.path("key").asString(); wishes.add(key);
            assertThat(wish.path("cardVerdict").asString()).isEqualTo(example.path("cardVerdicts").path(key).asString());
            if ("UNKNOWN".equals(wish.path("cardVerdict").asString())) assertThat(wish.path("cardQuote").isNull()).isTrue();
            else {
                assertThat(wish.path("cardQuote").asString()).isNotBlank();
                assertThat(example.at("/candidate/card").asString()).contains(wish.path("cardQuote").asString());
            }
            assertThat(wish.path("sources").size()).isEqualTo(example.at("/candidate/sources").size());
            for (JsonNode source : wish.path("sources")) {
                assertThat(source.propertyNames()).containsExactlyInAnyOrder("id", "verdict", "quote");
                JsonNode original = null;
                for (JsonNode supplied : example.at("/candidate/sources")) if (supplied.path("id").asString().equals(source.path("id").asString())) original = supplied;
                assertThat(original).as("Every source must be supplied").isNotNull();
                assertThat(original.path("type")).isEqualTo(example.at("/candidate/type"));
                if ("SUPPORTED".equals(source.path("verdict").asString())) {
                    assertThat(source.path("quote").asString()).isNotBlank();
                    assertThat(original.path("text").asString()).contains(source.path("quote").asString());
                    supportedSources.add(source.path("id").asString());
                } else { assertThat(source.path("verdict").asString()).isEqualTo("UNKNOWN"); assertThat(source.path("quote").isNull()).isTrue(); }
            }
        }
        assertThat(wishes).containsExactlyInAnyOrderElementsOf(example.path("cardVerdicts").propertyNames());
        assertThat(supportedSources).containsExactlyInAnyOrderElementsOf(JSON.convertValue(example.path("supportedSources"), List.class));
    }

    private double cosine(double[] left, double[] right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < left.length; index++) { dot += left[index] * right[index]; leftNorm += left[index] * left[index]; rightNorm += right[index] * right[index]; }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
