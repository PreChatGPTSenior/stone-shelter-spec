package lab.stoneshelter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.repositories.StoneCardReindexJobRepository;
import lab.stoneshelter.services.OutboxDispatcherService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.StoneCardReindexRunnerService;
import lab.stoneshelter.services.StoneCardReindexSettings;
import lab.stoneshelter.services.StoneCardReindexTransactionService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.StoneService;
import lab.stoneshelter.shared.StoneCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneCardReindexControllerTest extends IntegrationTest {
    private static final String JOBS = "/api/v1/stone-card-reindex-jobs";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    @Autowired private RestTestClient client;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StoneService stoneService;
    @Autowired private StoneCardReindexSettings settings;
    @MockitoSpyBean private StoneCardReindexTransactionService transactionService;
    @MockitoSpyBean private StoneCardReindexJobRepository jobRepository;
    @MockitoBean private StoneCardReindexRunnerService automaticRunner;
    @MockitoBean private OutboxDispatcherService outboxDispatcherService;
    @MockitoBean private OutboxKafkaTopicService outboxKafkaTopicService;
    @MockitoBean private StoneIndexConsumerService stoneIndexConsumerService;
    @MockitoBean private StoneIndexKafkaTopicService stoneIndexKafkaTopicService;
    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        reset((StoneCardReindexTransactionService) AopTestUtils.getUltimateTargetObject(transactionService));
        reset((StoneCardReindexJobRepository) AopTestUtils.getUltimateTargetObject(jobRepository));
        jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS reindex_http_reject_snapshot");
        jdbcTemplate.update("UPDATE stone_card_reindex_control SET active_job = NULL, creation_blocked = FALSE");
        jdbcTemplate.update("DELETE FROM stone_card_reindex_item");
        jdbcTemplate.update("DELETE FROM stone_card_reindex_job");
        for (long id : createdIds) {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", id);
            jdbcTemplate.update("DELETE FROM stone_index_source_version WHERE stone_id = ?", id);
            jdbcTemplate.update("DELETE FROM stone WHERE id = ?", id);
        }
    }

    @Test
    void selectedStartIsDurable201DeduplicatesAndRejectsOverlap() {
        long id = create();
        Map<String, Object> start = start(Map.of("target", "SELECTED", "stoneIds", List.of(id, id)));
        assertThat(start.keySet()).containsExactlyInAnyOrder("jobId", "status");
        assertThat(start.get("status")).isEqualTo("PENDING");
        var status = get(start.get("jobId").toString());
        assertThat(number(status, "totalCount")).isEqualTo(1);
        assertThat(status.get("target")).isEqualTo("SELECTED");
        problemStart(Map.of("target", "ALL"), 409);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_reindex_job", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void invalidSelectionReturns400WithoutStartingAJob(Map<String, Object> body) {
        problemStart(body, 400);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_reindex_job", Long.class)).isZero();
    }

    static Stream<Map<String, Object>> invalidSelections() {
        Map<String, Object> nullElement = new HashMap<>();
        nullElement.put("target", "SELECTED");
        nullElement.put("stoneIds", java.util.Arrays.asList((Long) null));
        return Stream.of(Map.of(), Map.of("target", "UNKNOWN"), Map.of("target", "SELECTED"),
                Map.of("target", "SELECTED", "stoneIds", List.of()), Map.of("target", "SELECTED", "stoneIds", List.of(0)),
                Map.of("target", "SELECTED", "stoneIds", List.of(-1)), Map.of("target", "ALL", "stoneIds", List.of(1)), nullElement);
    }

    @Test
    void missingSelectionsAndUnknownJobsAre404() {
        problemStart(Map.of("target", "SELECTED", "stoneIds", List.of(create(), 900002)), 404);
        client.get().uri(JOBS + "/" + UUID.randomUUID()).exchange().expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        client.get().uri(JOBS + "/not-a-uuid").exchange().expectStatus().isBadRequest();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_reindex_job", Long.class)).isZero();
    }

    @Test
    void fullJobBlocksCreationButPermitsDeleteAndReleasesAtEnqueued() {
        long id = create();
        var start = start(Map.of("target", "ALL"));
        client.post().uri("/api/v1/stones").body(stoneBody()).exchange().expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(id);
        new StoneCardReindexRunnerService(transactionService, settings).runBatch();
        var status = get(start.get("jobId").toString());
        assertThat(status.get("status")).isEqualTo("ENQUEUED");
        assertThat(number(status, "skippedCount")).isEqualTo(1);
        var created = client.post().uri("/api/v1/stones").body(stoneBody()).exchange().expectStatus().isCreated()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
        createdIds.add(((Number) created.get("id")).longValue());
    }

    @Test
    void jobStorageFailuresAre503ProblemDetail() {
        doThrow(new CannotCreateTransactionException("Storage unavailable")).when(
                (StoneCardReindexTransactionService) AopTestUtils.getUltimateTargetObject(transactionService)).start(any(), any());
        problemStart(Map.of("target", "ALL"), 503);
        reset((StoneCardReindexTransactionService) AopTestUtils.getUltimateTargetObject(transactionService));
        var start = start(Map.of("target", "ALL"));
        doThrow(new DataAccessResourceFailureException("Storage unavailable")).when((StoneCardReindexJobRepository) AopTestUtils.getUltimateTargetObject(jobRepository)).findById(any());
        client.get().uri(JOBS + "/" + start.get("jobId")).exchange().expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).expectBody().jsonPath("$.status").isEqualTo(503);
    }

    @Test
    void progressContainsDurableIndividualErrorsAndEnqueuedDoesNotClaimVectors() {
        long id = create();
        var start = start(Map.of("target", "SELECTED", "stoneIds", List.of(id)));
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT reindex_http_reject_snapshot CHECK ((payload->>'sourceVersion')::bigint = 1)");
        new StoneCardReindexRunnerService(transactionService, settings).runBatch();
        var status = get(start.get("jobId").toString());
        assertThat(status.get("status")).isEqualTo("ENQUEUED");
        assertThat(number(status, "failedCount")).isEqualTo(1);
        assertThat(number(status, "enqueuedCount")).isZero();
        assertThat((List<?>) status.get("errors")).hasSize(1);
        assertThat(status.get("createdAt")).isNotNull();
        assertThat(status.get("updatedAt")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding", Long.class)).isZero();
    }

    @Test
    void generatedJsonAndYamlDescribeOnlyTheAcceptedJobOperationsAndDtos() {
        Map<String, Object> document = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
        Map<?, ?> paths = (Map<?, ?>) document.get("paths");
        assertThat(((Map<?, ?>) paths.get(JOBS)).containsKey("post")).isTrue();
        assertThat(((Map<?, ?>) paths.get(JOBS + "/{jobId}")).containsKey("get")).isTrue();
        Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) document.get("components")).get("schemas");
        Map<?, ?> properties = (Map<?, ?>) ((Map<?, ?>) schemas.get("StoneCardReindexStatusResponse")).get("properties");
        assertThat(properties.size()).isEqualTo(12);
        assertThat(properties.containsKey("errors")).isTrue();
        assertThat(properties.containsKey("reconciledCount")).isTrue();
        String yaml = client.get().uri("/v3/api-docs.yaml").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(yaml).contains(JOBS, "StoneCardReindexStartRequest", "StoneCardReindexStatusResponse", "ENQUEUED");
    }

    private Map<String, Object> start(Map<String, Object> body) {
        return client.post().uri(JOBS).body(body).exchange().expectStatus().isCreated().expectHeader().doesNotExist("Location")
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }
    private Map<String, Object> get(String jobId) {
        return client.get().uri(JOBS + "/" + jobId).exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }
    private void problemStart(Map<String, Object> body, int status) {
        client.post().uri(JOBS).body(body).exchange().expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(status).jsonPath("$.detail").isNotEmpty();
    }
    private long create() {
        StoneCreateRequest request = new StoneCreateRequest();
        request.setName("Luna");
        request.setStoneType(StoneType.BASALT);
        request.setStoneSize(StoneSize.SMALL);
        request.setAdoptionStatus(AdoptionStatus.AVAILABLE);
        long id = stoneService.create(request).getId();
        createdIds.add(id);
        return id;
    }
    private Map<String, Object> stoneBody() { return Map.of("name", "Luna", "stoneType", "BASALT", "stoneSize", "SMALL", "adoptionStatus", "AVAILABLE"); }
    private long number(Map<String, Object> body, String field) { return ((Number) body.get(field)).longValue(); }
}
