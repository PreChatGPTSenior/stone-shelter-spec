package lab.stoneshelter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneReservationEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.mappers.dtos.StoneReservationEntityToStoneReservationCreateResponseMapper;
import lab.stoneshelter.repositories.StoneEntityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneReservationControllerTest extends IntegrationTest {
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    @Autowired private RestTestClient client;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StoneEntityRepository stoneEntityRepository;
    @MockitoSpyBean private StoneReservationEntityToStoneReservationCreateResponseMapper responseMapper;
    private final List<Long> stoneIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        reset(responseMapper);
        for (long id : stoneIds) jdbcTemplate.update("delete from stone where id = ?", id);
    }

    @Test
    void createsReservationAndReturnsChangedStatusWithoutExposingApplicantDataInReads() {
        long id = createStone(AdoptionStatus.AVAILABLE);
        client.post().uri("/api/v1/stones/search").body(Map.of()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.content[0].id").isEqualTo(id)
                .jsonPath("$.totalElements").isEqualTo(1);
        Map<String, Object> response = submit(id);
        assertThat(response.keySet()).containsExactlyInAnyOrder("id", "stoneId", "adoptionStatus", "createdAt");
        assertThat(((Number) response.get("stoneId")).longValue()).isEqualTo(id);
        assertThat(response.get("adoptionStatus")).isEqualTo("RESERVED");
        assertThat(response.get("createdAt").toString()).endsWith("Z");
        client.get().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk().expectBody(JSON_OBJECT).value(body -> {
            assertThat(body.get("adoptionStatus")).isEqualTo("RESERVED");
            assertThat(body).doesNotContainKeys("applicantName", "contactDetails", "reservation");
        });
        client.post().uri("/api/v1/stones/search").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filter", Map.of("adoptionStatus", "RESERVED")))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.content").isEmpty()
                .jsonPath("$.totalElements").isEqualTo(0);
        client.post().uri("/api/v1/stones/search").body(Map.of()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.content").isEmpty().jsonPath("$.totalElements").isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject("select contact_details from stone_reservation where stone_id = ?", String.class, id))
                .isEqualTo("contact me anywhere " + "x".repeat(3000));
        problem(Long.toString(id), validRequest(), 409);
        problem(Long.toString(id), Map.of("applicantName", "Another visitor", "contactDetails", "other contacts"), 409);
    }

    static Stream<Map<String, Object>> invalidRequests() {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (String field : List.of("applicantName", "contactDetails")) {
            var missing = new HashMap<>(validRequest());
            missing.remove(field);
            requests.add(missing);
            for (String value : new String[]{null, "", " ", "\t\n"}) {
                var invalid = new HashMap<>(validRequest());
                invalid.put(field, value);
                requests.add(invalid);
            }
        }
        return requests.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidApplicantInputWithoutChanges(Map<String, Object> request) {
        long id = createStone(AdoptionStatus.AVAILABLE);
        problem(Long.toString(id), request, 400);
        assertUnchanged(id);
    }

    @Test
    void rejectsMissingMalformedBodiesAndMalformedPath() {
        long id = createStone(AdoptionStatus.AVAILABLE);
        problem(Long.toString(id), null, 400);
        problem(Long.toString(id), "{", 400);
        problem("abc", validRequest(), 400);
        assertUnchanged(id);
    }

    @Test
    void rejectsMissingOrUnavailableStone() {
        problem(Long.toString(Long.MAX_VALUE), validRequest(), 404);
        for (var adoptionStatus : List.of(AdoptionStatus.RESERVED, AdoptionStatus.ADOPTED)) {
            long id = createStone(adoptionStatus);
            problem(Long.toString(id), validRequest(), 409);
            assertThat(jdbcTemplate.queryForObject("select count(*) from stone_reservation where stone_id = ?", Long.class, id)).isZero();
            assertThat(stoneEntityRepository.findById(id).orElseThrow().getAdoptionStatus()).isEqualTo(adoptionStatus);
        }
    }

    @Test
    void persistenceErrorReturnsProblemDetailRollsBackAndAllowsRetry() {
        long id = createStone(AdoptionStatus.AVAILABLE);
        doThrow(new DataIntegrityViolationException("Injected failure after database flush"))
                .when(responseMapper).toDto(any(StoneReservationEntity.class));
        problem(Long.toString(id), validRequest(), 500);
        assertUnchanged(id);
        reset(responseMapper);
        submit(id);
    }

    @Test
    void concurrentHttpRequestsReturnOneCreatedAndOtherConflicts() throws Exception {
        long id = createStone(AdoptionStatus.AVAILABLE);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                results.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("HTTP test start timed out");
                    return client.post().uri(path(Long.toString(id))).contentType(MediaType.APPLICATION_JSON).body(validRequest())
                            .exchange().expectBody(String.class).returnResult().getStatus().value();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409, 409, 409);
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from stone_reservation where stone_id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void existingDeleteReturnsIdentifierAndRemovesReservation() {
        long id = createStone(AdoptionStatus.AVAILABLE);
        submit(id);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).value(body -> assertThat(((Number) body.get("id")).longValue()).isEqualTo(id));
        assertThat(jdbcTemplate.queryForObject("select count(*) from stone_reservation where stone_id = ?", Long.class, id)).isZero();
    }

    private Map<String, Object> submit(long id) {
        return client.post().uri(path(Long.toString(id))).contentType(MediaType.APPLICATION_JSON).body(validRequest())
                .exchange().expectStatus().isCreated().expectHeader().doesNotExist("Location")
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    private void problem(String id, Object body, int status) {
        var request = client.post().uri(path(id)).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.body(body);
        request.exchange().expectStatus().isEqualTo(status).expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(JSON_OBJECT).value(problemDetail -> {
                    assertThat(problemDetail).containsKeys("type", "title", "status", "detail");
                    assertThat(problemDetail.get("status")).isEqualTo(status);
                });
    }

    private void assertUnchanged(long id) {
        assertThat(stoneEntityRepository.findById(id).orElseThrow().getAdoptionStatus()).isEqualTo(AdoptionStatus.AVAILABLE);
        assertThat(jdbcTemplate.queryForObject("select count(*) from stone_reservation where stone_id = ?", Long.class, id)).isZero();
    }

    private static String path(String id) { return "/api/v1/stones/" + id + "/reservations"; }

    private static Map<String, Object> validRequest() {
        return Map.of("applicantName", "  Visitor 石  ", "contactDetails", "contact me anywhere " + "x".repeat(3000));
    }

    private long createStone(AdoptionStatus adoptionStatus) {
        var stoneEntity = new StoneEntity();
        stoneEntity.setName("Reservation HTTP test");
        stoneEntity.setStoneType(StoneType.values()[0]);
        stoneEntity.setStoneSize(StoneSize.SMALL);
        stoneEntity.setAdoptionStatus(adoptionStatus);
        stoneEntity.setAdmissionDate(Instant.parse("2026-01-01T00:00:00Z"));
        stoneEntity = stoneEntityRepository.saveAndFlush(stoneEntity);
        stoneIds.add(stoneEntity.getId());
        return stoneEntity.getId();
    }
}
