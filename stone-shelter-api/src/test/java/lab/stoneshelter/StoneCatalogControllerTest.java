package lab.stoneshelter;

import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.services.OutboxDispatcherService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.StoneCardReindexRunnerService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneCatalogControllerTest extends IntegrationTest {
    private static final String STONES = "/api/v1/stones";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};
    @Autowired private JdbcTemplate jdbc;
    private final List<Long> createdIds = new ArrayList<>();
    private final List<Long> publicationIds = new ArrayList<>();
    @MockitoBean private OutboxDispatcherService outboxDispatcherService;
    @MockitoBean private OutboxKafkaTopicService outboxKafkaTopicService;
    @MockitoBean private StoneIndexKafkaTopicService stoneIndexKafkaTopicService;
    @MockitoBean private StoneIndexConsumerService stoneIndexConsumerService;
    @MockitoBean private StoneCardReindexRunnerService stoneCardReindexRunnerService;
    @Autowired private RestTestClient client;
    @MockitoSpyBean private Clock clock;

    @BeforeEach
    void setCreationTime() {
        doReturn(Instant.parse("2000-01-01T00:00:00Z")).when(clock).instant();
    }

    @AfterEach
    void cleanUpOwnStones() {
        reset(clock);
        jdbc.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS catalog_test_reject_index");
        for (long id : publicationIds) {
            jdbc.update("DELETE FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", id);
            jdbc.update("DELETE FROM stone_index_source_version WHERE stone_id = ?", id);
        }
        org.junit.jupiter.api.Assertions.assertAll("Delete all Stones created by this test",
                List.copyOf(createdIds).stream().map(id -> () -> jdbc.update("DELETE FROM stone WHERE id = ?", id)));
    }

    @ParameterizedTest(name = "invalid create: {0}")
    @MethodSource("invalidCreateBodies")
    void invalidCreateReturnsProblemDetail(String reason, Map<String, Object> body) {
        long before = jdbc.queryForObject("SELECT count(*) FROM stone", Long.class);
        problem(HttpMethod.POST, STONES, body, 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone", Long.class)).isEqualTo(before);
    }

    @ParameterizedTest(name = "invalid update preserves data: {0}")
    @MethodSource("invalidUpdateBodies")
    void invalidUpdateLeavesStoneUnchanged(String reason, Map<String, Object> body) {
        Map<String, Object> original = create(validStone());
        problem(HttpMethod.PUT, STONES + "/" + id(original), body, 400);
        assertThat(get(id(original))).isEqualTo(original);
    }

    static Stream<Arguments> invalidCreateBodies() { return invalidStoneBodies(false); }

    static Stream<Arguments> invalidUpdateBodies() { return invalidStoneBodies(true); }

    static Stream<Arguments> invalidStoneBodies(boolean update) {
        List<Arguments> cases = new ArrayList<>();
        for (String field : update ? List.of("name", "stoneType", "stoneSize", "adoptionStatus", "admissionDate")
                : List.of("name", "stoneType", "stoneSize", "adoptionStatus")) {
            Map<String, Object> absent = validStone(update);
            absent.remove(field);
            cases.add(Arguments.of(field + " missing", absent));
            cases.add(Arguments.of(field + " null", changed(field, null, update)));
        }
        for (String name : List.of("", " ", "\t\n", "x".repeat(121))) {
            cases.add(Arguments.of("invalid name", changed("name", name, update)));
        }
        cases.add(Arguments.of("biography over limit", changed("biography", "x".repeat(2049), update)));
        cases.add(Arguments.of("photo over limit", changed("photo", "x".repeat(501), update)));
        cases.add(Arguments.of("unknown type", changed("stoneType", "GRANITE_X", update)));
        cases.add(Arguments.of("lowercase type", changed("stoneType", "granite", update)));
        cases.add(Arguments.of("unknown size", changed("stoneSize", "HUGE", update)));
        cases.add(Arguments.of("unknown status", changed("adoptionStatus", "GONE", update)));
        if (update) for (String timestamp : List.of("not-a-timestamp", "2000-02-30T08:00:00Z",
                "2000-01-01", "2000-01-01T08:00:00", Instant.now().plusSeconds(3600).toString())) {
            cases.add(Arguments.of("invalid/future timestamp " + timestamp, changed("admissionDate", timestamp, true)));
        }
        cases.add(Arguments.of("wrong JSON type", changed("stoneSize", Map.of("value", "SMALL"), update)));
        return cases.stream();
    }

    @ParameterizedTest(name = "invalid search: {0}")
    @MethodSource("invalidSearchBodies")
    void invalidSearchReturnsProblemDetail(String reason, Map<String, Object> body) {
        problem(HttpMethod.POST, STONES + "/search", body, 400);
    }

    static Stream<Arguments> invalidSearchBodies() {
        return Stream.of(
                Arguments.of("negative page", Map.of("page", -1)),
                Arguments.of("zero size", Map.of("size", 0)),
                Arguments.of("negative size", Map.of("size", -1)),
                Arguments.of("size above maximum", Map.of("size", 25)),
                Arguments.of("nonnumeric page", Map.of("page", "abc")),
                Arguments.of("unknown type filter", Map.of("filter", Map.of("stoneType", "GRANITE_X"))),
                Arguments.of("lowercase type filter", Map.of("filter", Map.of("stoneType", "granite"))),
                Arguments.of("unknown size filter", Map.of("filter", Map.of("stoneSize", "HUGE"))),
                Arguments.of("unknown status filter", Map.of("filter", Map.of("adoptionStatus", "GONE"))),
                Arguments.of("sort outside whitelist", Map.of("sort", Map.of("field", "biography"))),
                Arguments.of("empty sort field", Map.of("sort", Map.of("field", ""))),
                Arguments.of("invalid direction", Map.of("sort", Map.of("direction", "sideways"))),
                Arguments.of("multiple criteria", Map.of("sort", List.of(
                        Map.of("field", "name"), Map.of("field", "stoneSize")))));
    }

    @Test
    void malformedJsonAndMissingBodiesAreRejected() {
        problem(HttpMethod.POST, STONES, "{\"name\":", 400);
        problem(HttpMethod.POST, STONES + "/search", "{\"filter\":", 400);
        problem(HttpMethod.POST, STONES, null, 400);
        problem(HttpMethod.POST, STONES + "/search", null, 400);
        Map<String, Object> original = create(validStone());
        problem(HttpMethod.PUT, STONES + "/" + id(original), "{\"name\":", 400);
        assertThat(get(id(original))).isEqualTo(original);
    }

    @Test
    void malformedIdentifiersAreRejected() {
        problem(HttpMethod.GET, STONES + "/abc", null, 400);
        problem(HttpMethod.PUT, STONES + "/abc", validStone(), 400);
        problem(HttpMethod.DELETE, STONES + "/abc", null, 400);
    }

    @Test
    void missingStoneReturnsNotFoundForReadUpdateAndDelete() {
        long id = id(create(validStone()));
        delete(id);
        problem(HttpMethod.GET, STONES + "/" + id, null, 404);
        problem(HttpMethod.PUT, STONES + "/" + id, validStone(true), 404);
        problem(HttpMethod.DELETE, STONES + "/" + id, null, 404);
    }

    @Test
    void exactLengthLimitsAreAcceptedOnCreateAndUpdate() {
        Map<String, Object> body = validStone();
        body.put("name", "n".repeat(120));
        body.put("biography", "b".repeat(2048));
        body.put("photo", "p".repeat(500));
        Map<String, Object> stone = create(body);
        assertThat(stone).containsAllEntriesOf(body);
        body.put("name", "m".repeat(120));
        assertThat(update(id(stone), body)).containsAllEntriesOf(body);
        assertThat(get(id(stone))).containsAllEntriesOf(body);
    }

    @Test
    void fullReplacementPersistsEveryFieldWithoutChangingIdentity() {
        long id = id(create(validStone()));
        var replacement = changed("name", "Updated Stone");
        replacement.put("stoneType", "MARBLE");
        replacement.put("stoneSize", "LARGE");
        replacement.put("adoptionStatus", "ADOPTED");
        replacement.put("admissionDate", "2001-06-01T12:00:00Z");
        replacement.put("biography", "New history");
        replacement.put("photo", "New placeholder");
        assertThat(update(id, replacement)).containsAllEntriesOf(replacement);
        var stored = get(id);
        assertThat(stored).containsAllEntriesOf(replacement);
        assertThat(id(stored)).isEqualTo(id);
    }

    @Test
    void duplicateNamesHaveDistinctIdentities() {
        Map<String, Object> first = create(validStone());
        Map<String, Object> second = create(validStone());
        assertThat(id(first)).isNotEqualTo(id(second));
        assertThat(get(id(first))).isEqualTo(first);
        assertThat(get(id(second))).isEqualTo(second);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(lab.stoneshelter.enums.StoneType.class)
    void everySupportedStoneTypeCanBeCreated(lab.stoneshelter.enums.StoneType type) {
        Map<String, Object> stone = create(changed("stoneType", type.name()));
        assertThat(stone).containsEntry("stoneType", type.name());
        assertThat(get(id(stone))).isEqualTo(stone);
    }

    @ParameterizedTest
    @MethodSource("individualFilters")
    void eachFilterExcludesNonmatchingStones(String field, String match, String other) {
        long matchId = id(create(changed(field, match)));
        create(changed(field, other));
        Map<String, Object> page = search(Map.of("filter", Map.of(field, match)));
        assertThat(ids(page)).containsExactly(matchId);
        assertThat(number(page, "totalElements")).isEqualTo(1);
    }

    static Stream<Arguments> individualFilters() {
        return Stream.of(Arguments.of("stoneType", "GRANITE", "BASALT"),
                Arguments.of("stoneSize", "SMALL", "LARGE"),
                Arguments.of("adoptionStatus", "AVAILABLE", "RESERVED"));
    }

    @Test
    void optionalFieldsCanBeOmittedClearedAndPreserved() {
        Map<String, Object> body = validStone();
        body.remove("biography");
        body.put("photo", "");
        Map<String, Object> stone = create(body);
        assertThat(stone).containsEntry("biography", null).containsEntry("photo", "");
        assertThat(get(id(stone))).containsEntry("photo", "");
        body.put("biography", "History");
        body.put("photo", "placeholder");
        assertThat(update(id(stone), body)).containsAllEntriesOf(body);
        assertThat(get(id(stone))).containsAllEntriesOf(body);
        body.remove("biography");
        body.remove("photo");
        assertThat(update(id(stone), body)).containsEntry("biography", null).containsEntry("photo", null);
        assertThat(get(id(stone))).containsEntry("biography", null).containsEntry("photo", null);
        body.put("photo", "");
        assertThat(update(id(stone), body)).containsEntry("photo", "");
        assertThat(get(id(stone))).containsEntry("photo", "");
        body.put("photo", "  ");
        assertThat(update(id(stone), body)).containsEntry("photo", "  ");
        assertThat(get(id(stone))).containsEntry("photo", "  ");
        body.put("photo", null);
        body.put("biography", null);
        assertThat(update(id(stone), body)).containsEntry("biography", null).containsEntry("photo", null);
    }

    @Test
    void updatedTimestampOffsetsReferToTheSameInstantAndResponsesUseUtc() {
        long id = id(create(validStone()));
        Map<String, Object> stone = update(id, changed("admissionDate", "2000-01-01T10:00:00+02:00"));
        assertThat(stone).containsEntry("admissionDate", "2000-01-01T08:00:00Z");
        assertThat(get(id).get("admissionDate")).isEqualTo(stone.get("admissionDate"));
    }

    @Test
    void creationWithoutAdmissionDatePersistsTheServerInstantInEveryRead() {
        assertServerAdmissionDate(validStone());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1900-01-01T00:00:00Z", "2099-01-01T00:00:00Z", "not-a-timestamp"})
    void obsoleteClientAdmissionDateCannotOverrideTheServerInstant(String admissionDate) {
        assertServerAdmissionDate(changed("admissionDate", admissionDate));
    }

    private void assertServerAdmissionDate(Map<String, Object> body) {
        Instant serverTime = Instant.parse("2026-10-07T20:15:29.123456Z");
        doReturn(serverTime).when(clock).instant();
        var stone = create(body);
        assertThat(stone).containsEntry("admissionDate", serverTime.toString());
        assertThat(get(id(stone))).isEqualTo(stone);
        assertThat(jdbc.queryForObject("SELECT admission_date FROM stone WHERE id = ?", java.sql.Timestamp.class, id(stone)).toInstant())
                .isEqualTo(serverTime);
        assertThat(items(search(Map.of())).getFirst()).containsEntry("admissionDate", serverTime.toString());
    }

    @Test
    void emptyCatalogHasOnlyTheContractedPageFieldsAndDefaults() {
        Map<String, Object> page = search(Map.of());
        assertThat(page).containsOnlyKeys("content", "page", "size", "totalElements");
        assertThat(page).containsEntry("page", 0).containsEntry("size", 12);
        assertThat(number(page, "totalElements")).isZero();
        assertThat(items(page)).isEmpty();
    }

    @Test
    void omittedNullAndEmptyOptionalSearchObjectsUseTheSameDefaults() {
        create(changed("name", "ZETA"));
        create(changed("name", "ALPHA"));
        Map<String, Object> defaults = search(Map.of());
        assertThat(search(Map.of("sort", Map.of("direction", "desc")))).isEqualTo(defaults);
        assertThat(search(Map.of("filter", Map.of(), "sort", Map.of()))).isEqualTo(defaults);
        Map<String, Object> nulls = new HashMap<>();
        for (String field : List.of("filter", "page", "size", "sort")) {
            nulls.put(field, null);
        }
        assertThat(search(nulls)).isEqualTo(defaults);
    }

    @Test
    void defaultSortingUsesLatestAdmissionDateWithAscendingIdTies() {
        var latest = changed("name", "ZETA");
        long latest1 = id(createAt("2002-01-01T00:00:00Z", latest));
        var oldest = changed("name", "ALPHA");
        long oldestId = id(createAt("2000-01-01T00:00:00Z", oldest));
        long latest2 = id(createAt("2002-01-01T00:00:00Z", latest));
        var middle = changed("name", "BETA");
        long middleId = id(createAt("2001-01-01T00:00:00Z", middle));

        assertThat(ids(search(Map.of()))).containsExactly(latest1, latest2, middleId, oldestId);
        assertThat(ids(search(Map.of("sort", Map.of())))).containsExactly(latest1, latest2, middleId, oldestId);
        var nullSort = new HashMap<String, Object>();
        nullSort.put("sort", null);
        assertThat(ids(search(nullSort))).containsExactly(latest1, latest2, middleId, oldestId);
        var nullFields = new HashMap<String, Object>();
        nullFields.put("field", null);
        nullFields.put("direction", null);
        assertThat(ids(search(Map.of("sort", nullFields)))).containsExactly(latest1, latest2, middleId, oldestId);

        var firstPage = search(Map.of("size", 1));
        assertThat(ids(firstPage)).containsExactly(latest1);
        assertThat(search(Map.of("size", 1))).isEqualTo(firstPage);
        var secondPage = search(Map.of("size", 1, "page", 1));
        assertThat(ids(secondPage)).containsExactly(latest2);
        assertThat(search(Map.of("size", 1, "page", 1))).isEqualTo(secondPage);
        assertThat(ids(search(Map.of("sort", Map.of("direction", "asc")))))
                .containsExactly(oldestId, middleId, latest1, latest2);

        assertThat(ids(search(Map.of("sort", Map.of("field", "admissionDate")))))
                .containsExactly(latest1, latest2, middleId, oldestId);
        assertThat(ids(search(Map.of("sort", Map.of("field", "admissionDate", "direction", "desc")))))
                .containsExactly(latest1, latest2, middleId, oldestId);
        assertThat(ids(search(Map.of("sort", Map.of("field", "admissionDate", "direction", "asc")))))
                .containsExactly(oldestId, middleId, latest1, latest2);
        nullFields.put("field", "admissionDate");
        assertThat(ids(search(Map.of("sort", nullFields))))
                .containsExactly(latest1, latest2, middleId, oldestId);
        assertThat(ids(search(Map.of("page", 1, "size", 1,
                "sort", Map.of("field", "admissionDate", "direction", "desc")))))
                .containsExactly(latest2);
    }

    @Test
    void catalogPaginationIsStableAndSupportsAnOutOfRangePage() {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            expected.add(id(create(changed("name", "Stone " + String.format("%02d", i)))));
        }
        Map<String, Object> first = search(Map.of());
        assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0, 12));
        assertThat(number(first, "totalElements")).isEqualTo(15);
        assertThat(search(Map.of())).isEqualTo(first);
        var second = search(Map.of("page", 1));
        assertThat(ids(second)).containsExactlyElementsOf(expected.subList(12, 15));
        assertThat(second).containsEntry("page", 1).containsEntry("size", 12);
        Map<String, Object> beyond = search(Map.of("page", 99));
        assertThat(items(beyond)).isEmpty();
        assertThat(number(beyond, "totalElements")).isEqualTo(15);
        assertThat(items(search(Map.of("page", Integer.MAX_VALUE)))).isEmpty();
        assertThat(items(search(Map.of("size", 13)))).hasSize(13);
        assertThat(items(search(Map.of("size", 24)))).hasSize(15);
    }

    @Test
    void filtersCombineWithAndAndCountOnlyMatchingStones() {
        Map<String, Object> match = changed("stoneType", "GRANITE");
        long matchingId = id(create(match));
        long secondMatch = id(create(match));
        create(match);
        var wrongSize = new HashMap<>(match);
        wrongSize.put("stoneSize", "LARGE");
        create(wrongSize);
        var wrongType = new HashMap<>(match);
        wrongType.put("stoneType", "BASALT");
        create(wrongType);
        for (String adoptionStatus : List.of("RESERVED", "ADOPTED")) {
            var unavailable = new HashMap<>(match);
            unavailable.put("adoptionStatus", adoptionStatus);
            create(unavailable);
        }
        create(changed("stoneSize", "LARGE"));
        Map<String, Object> result = search(Map.of("filter", Map.of("stoneType", "GRANITE",
                "stoneSize", "SMALL", "adoptionStatus", "AVAILABLE"), "size", 1));
        assertThat(ids(result)).containsExactly(matchingId);
        assertThat(number(result, "totalElements")).isEqualTo(3);
        Map<String, Object> next = search(Map.of("filter", Map.of("stoneType", "GRANITE",
                "stoneSize", "SMALL", "adoptionStatus", "AVAILABLE"), "page", 1, "size", 1));
        assertThat(ids(next)).containsExactly(secondMatch);
        assertThat(number(next, "totalElements")).isEqualTo(3);
        assertThat(items(search(Map.of("filter", Map.of("stoneType", "SLATE"))))).isEmpty();
    }

    static Stream<Map<String, Object>> availableCatalogRequests() {
        var requests = new ArrayList<Map<String, Object>>();
        requests.add(Map.of());
        requests.add(Map.of("filter", Map.of()));
        requests.add(Map.of("filter", Map.of("adoptionStatus", "AVAILABLE")));
        var nullFilter = new HashMap<String, Object>();
        nullFilter.put("filter", null);
        requests.add(nullFilter);
        var nullStatus = new HashMap<String, Object>();
        nullStatus.put("adoptionStatus", null);
        requests.add(Map.of("filter", nullStatus));
        return requests.stream();
    }

    @ParameterizedTest
    @MethodSource("availableCatalogRequests")
    void catalogVisibilityFiltersBeforePaginationAndCountsOnlyAvailable(Map<String, Object> request) {
        create(changed("adoptionStatus", "RESERVED"));
        long firstAvailableId = id(create(validStone()));
        create(changed("adoptionStatus", "ADOPTED"));
        long secondAvailableId = id(create(validStone()));
        var pageRequest = new HashMap<>(request);
        pageRequest.put("size", 1);
        var firstPage = search(pageRequest);
        assertThat(ids(firstPage)).containsExactly(firstAvailableId);
        assertThat(number(firstPage, "totalElements")).isEqualTo(2);
        assertThat(items(firstPage).getFirst()).containsEntry("adoptionStatus", "AVAILABLE");
        pageRequest.put("page", 1);
        var secondPage = search(pageRequest);
        assertThat(ids(secondPage)).containsExactly(secondAvailableId);
        assertThat(number(secondPage, "totalElements")).isEqualTo(2);
        pageRequest.put("page", 2);
        var beyondLastPage = search(pageRequest);
        assertThat(items(beyondLastPage)).isEmpty();
        assertThat(number(beyondLastPage, "totalElements")).isEqualTo(2);
        pageRequest.put("page", Integer.MAX_VALUE);
        pageRequest.put("size", 2);
        var largeOffsetPage = search(pageRequest);
        assertThat(items(largeOffsetPage)).isEmpty();
        assertThat(number(largeOffsetPage, "totalElements")).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVED", "ADOPTED"})
    void unavailableStatusCannotOverrideCatalogVisibilityAndDirectLookupStillWorks(String adoptionStatus) {
        long unavailableId = id(create(changed("adoptionStatus", adoptionStatus)));
        create(validStone());
        var page = search(Map.of("filter", Map.of("adoptionStatus", adoptionStatus)));
        assertThat(items(page)).isEmpty();
        assertThat(number(page, "totalElements")).isZero();
        assertThat(get(unavailableId)).containsEntry("adoptionStatus", adoptionStatus);
    }

    @Test
    void catalogIsEmptyWhenEveryStoneIsUnavailable() {
        create(changed("adoptionStatus", "RESERVED"));
        create(changed("adoptionStatus", "ADOPTED"));
        var page = search(Map.of());
        assertThat(items(page)).isEmpty();
        assertThat(number(page, "totalElements")).isZero();
    }

    @Test
    void namesSortBothWaysAndEqualNamesAlwaysUseAscendingId() {
        long beta = id(create(changed("name", "BETA")));
        long alpha1 = id(create(changed("name", "ALPHA")));
        long gamma = id(create(changed("name", "GAMMA")));
        long alpha2 = id(create(changed("name", "ALPHA")));
        assertThat(ids(search(Map.of()))).containsExactly(beta, alpha1, gamma, alpha2);
        assertThat(ids(search(Map.of("sort", Map.of("field", "name")))))
                .containsExactly(alpha1, alpha2, beta, gamma);
        assertThat(ids(search(Map.of("sort", Map.of("field", "name", "direction", "desc")))))
                .containsExactly(gamma, beta, alpha1, alpha2);
    }

    @Test
    void sizeSortUsesDomainOrderAndAscendingIdForTies() {
        long large = id(create(changed("stoneSize", "LARGE")));
        long small1 = id(create(changed("stoneSize", "SMALL")));
        long medium = id(create(changed("stoneSize", "MEDIUM")));
        long small2 = id(create(changed("stoneSize", "SMALL")));
        assertThat(ids(search(Map.of("sort", Map.of("field", "stoneSize", "direction", "asc")))))
                .containsExactly(small1, small2, medium, large);
        assertThat(ids(search(Map.of("sort", Map.of("field", "stoneSize", "direction", "desc")))))
                .containsExactly(large, medium, small1, small2);
    }

    @Test
    void filteringAndSizeSortingApplyTogether() {
        var largeGranite = changed("stoneType", "GRANITE");
        largeGranite.put("stoneSize", "LARGE");
        long large = id(create(largeGranite));
        long small = id(create(changed("stoneType", "GRANITE")));
        create(changed("stoneSize", "LARGE"));
        var page = search(Map.of("filter", Map.of("stoneType", "GRANITE"),
                "sort", Map.of("field", "stoneSize", "direction", "desc")));
        assertThat(ids(page)).containsExactly(large, small);
        assertThat(number(page, "totalElements")).isEqualTo(2);
    }

    @Test
    void deletedStoneDisappearsAndRecreationGetsANewId() {
        long id = id(create(validStone()));
        delete(id);
        problem(HttpMethod.GET, STONES + "/" + id, null, 404);
        assertThat(items(search(Map.of()))).isEmpty();
        assertThat(id(create(validStone()))).isNotEqualTo(id);
    }

    @Test
    void outboxFailureReturns500AndRollsBackEachHttpMutation() {
        long stoneId = id(create(validStone()));
        long before = jdbc.queryForObject("SELECT count(*) FROM stone", Long.class);
        jdbc.execute("ALTER TABLE outbox_event ADD CONSTRAINT catalog_test_reject_index CHECK (payload->>'name' <> 'Rejected')");
        problem(HttpMethod.POST, STONES, changed("name", "Rejected"), 500);
        problem(HttpMethod.PUT, STONES + "/" + stoneId, changed("name", "Rejected", true), 500);
        jdbc.execute("ALTER TABLE outbox_event DROP CONSTRAINT catalog_test_reject_index");
        jdbc.execute("ALTER TABLE outbox_event ADD CONSTRAINT catalog_test_reject_index CHECK (event_type <> 'STONE_CARD_INDEX_DELETED')");
        problem(HttpMethod.DELETE, STONES + "/" + stoneId, null, 500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone", Long.class)).isEqualTo(before);
        assertThat(get(stoneId).get("name")).isEqualTo("Basalt Buddy");
        assertThat(jdbc.queryForObject("SELECT source_version FROM stone_index_source_version WHERE stone_id = ?", Long.class, stoneId)).isEqualTo(1);
    }

    @Test
    void generatedCatalogContractDoesNotExposeEventOrVersionFields() {
        Map<String, Object> document = client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
        Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) document.get("components")).get("schemas");
        for (String schema : List.of("StoneCreateResponse", "StoneUpdateResponse", "StoneResponse")) {
            Map<?, ?> properties = (Map<?, ?>) ((Map<?, ?>) schemas.get(schema)).get("properties");
            assertThat(properties.containsKey("eventId")).isFalse();
            assertThat(properties.containsKey("sourceVersion")).isFalse();
            assertThat(properties.containsKey("schemaVersion")).isFalse();
            assertThat(properties.containsKey("id")).isTrue();
        }
    }

    private Map<String, Object> create(Map<String, Object> body) {
        var response = client.post().uri(STONES).body(body).exchange();
        Map<String, Object> stone = response.expectBody(JSON_OBJECT).returnResult().getResponseBody();
        trackCreatedStone(stone);
        response.expectStatus().isCreated();
        assertThat(stone).doesNotContainKeys("eventId", "sourceVersion", "schemaVersion");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", Long.class, id(stone))).isEqualTo(1);
        assertThat(stone).isNotNull();
        assertThat(stone.remove("photos")).as("Legacy creation has an empty managed gallery").isEqualTo(List.of());
        response.expectHeader().doesNotExist("Location");
        return stone;
    }

    private Map<String, Object> get(long id) {
        var stone = client.get().uri(STONES + "/" + id).exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
        assertThat(stone.remove("photos")).as("Legacy stones have an empty managed gallery").isEqualTo(List.of());
        return stone;
    }

    private Map<String, Object> update(long id, Map<String, Object> body) {
        var replacement = new HashMap<>(body);
        replacement.putIfAbsent("admissionDate", "2000-01-01T00:00:00Z");
        Map<String, Object> stone = client.put().uri(STONES + "/" + id).body(replacement)
                .exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
        assertThat(id(stone)).isEqualTo(id);
        return stone;
    }

    private void delete(long id) {
        client.delete().uri(STONES + "/" + id).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
        createdIds.remove(id);
    }

    @Test
    void pluralFiltersCombineGroupsAndPreserveLegacyConstraints() {
        long small = id(create(validStone()));
        long medium = id(create(changed("stoneSize", "MEDIUM")));
        long granite = id(create(changed("stoneType", "GRANITE")));
        create(changed("stoneSize", "LARGE"));
        var filter = new HashMap<String, Object>();
        filter.put("stoneSizes", List.of("SMALL", "MEDIUM", "SMALL"));
        filter.put("stoneTypes", List.of("BASALT"));
        assertThat(ids(search(Map.of("filter", filter)))).containsExactly(small, medium);
        var page = search(Map.of("filter", filter, "size", 1, "page", 1));
        assertThat(ids(page)).containsExactly(medium);
        assertThat(number(page, "totalElements")).isEqualTo(2);
        filter.put("stoneTypes", List.of("BASALT", "GRANITE"));
        assertThat(ids(search(Map.of("filter", filter)))).containsExactly(small, medium, granite);
        filter.put("stoneSize", "LARGE");
        assertThat(ids(search(Map.of("filter", filter)))).isEmpty();
        filter.remove("stoneSize");
        filter.put("stoneSizes", List.of());
        filter.put("stoneTypes", null);
        assertThat(number(search(Map.of("filter", filter)), "totalElements")).isEqualTo(4);
        filter.put("adoptionStatus", "ADOPTED");
        assertThat(ids(search(Map.of("filter", filter)))).isEmpty();
    }

    @Test
    void dateFiltersIncludeWholeUtcDaysAndSupportOpenBounds() {
        long before = id(createAt("2000-01-01T23:59:59Z", validStone()));
        long start = id(createAt("2000-01-02T00:00:00Z", validStone()));
        long end = id(createAt("2000-01-02T23:59:59.999999Z", validStone()));
        long after = id(createAt("2000-01-03T00:00:00Z", validStone()));
        assertThat(ids(search(Map.of("filter", Map.of("admissionDateFrom", "2000-01-02",
                "admissionDateTo", "2000-01-02"))))).containsExactly(end, start);
        assertThat(ids(search(Map.of("filter", Map.of("admissionDateFrom", "2000-01-02")))))
                .containsExactly(after, end, start);
        assertThat(ids(search(Map.of("filter", Map.of("admissionDateTo", "2000-01-02")))))
                .containsExactly(end, start, before);
    }

    @Test
    void invalidPluralFiltersAndDateRangesReturnProblemDetail() {
        for (var filter : List.of(
                Map.of("stoneSizes", List.of("UNKNOWN")),
                Map.of("stoneTypes", List.of("UNKNOWN")),
                Map.of("stoneSizes", java.util.Arrays.asList("SMALL", null)),
                Map.of("stoneTypes", java.util.Arrays.asList("BASALT", null)),
                Map.of("admissionDateFrom", "not-a-date"),
                Map.of("admissionDateTo", "2000-02-30"),
                Map.of("admissionDateFrom", "2000-01-03", "admissionDateTo", "2000-01-02"))) {
            problem(HttpMethod.POST, STONES + "/search", Map.of("filter", filter), 400);
        }
    }

    private Map<String, Object> search(Map<String, Object> body) {
        return client.post().uri(STONES + "/search").body(body).exchange()
                .expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    private void problem(HttpMethod method, String path, Object body, int status) {
        var request = client.method(method).uri(path);
        var response = body == null ? request.exchange()
                : request.contentType(MediaType.APPLICATION_JSON).body(body).exchange();
        if (method == HttpMethod.POST && path.equals(STONES)) {
            trackCreatedStone(response.expectBody(JSON_OBJECT).returnResult().getResponseBody());
        }
        response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.title").isNotEmpty()
                .jsonPath("$.detail").isNotEmpty();
    }

    private static Map<String, Object> validStone() {
        return validStone(false);
    }

    private static Map<String, Object> validStone(boolean update) {
        var body = new HashMap<String, Object>(Map.of("name", "Basalt Buddy", "stoneType", "BASALT",
                "stoneSize", "SMALL", "adoptionStatus", "AVAILABLE",
                "biography", "History", "photo", "placeholder"));
        if (update) body.put("admissionDate", "2000-01-01T00:00:00Z");
        return body;
    }

    private void trackCreatedStone(Map<?, ?> body) {
        if (body != null && body.get("id") instanceof Number id) {
            createdIds.add(id.longValue());
            publicationIds.add(id.longValue());
        }
    }

    private static Map<String, Object> changed(String field, Object value) {
        return changed(field, value, false);
    }

    private static Map<String, Object> changed(String field, Object value, boolean update) {
        Map<String, Object> body = validStone(update);
        body.put(field, value);
        return body;
    }

    private Map<String, Object> createAt(String instant, Map<String, Object> body) {
        doReturn(Instant.parse(instant)).when(clock).instant();
        return create(body);
    }

    private static long number(Map<String, Object> body, String field) {
        return ((Number) body.get(field)).longValue();
    }

    private static long id(Map<String, Object> stone) {
        return number(stone, "id");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    private static List<Long> ids(Map<String, Object> page) {
        return items(page).stream().map(StoneCatalogControllerTest::id).toList();
    }
}
