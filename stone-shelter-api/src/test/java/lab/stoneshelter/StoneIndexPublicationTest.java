package lab.stoneshelter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.services.OutboxDeliverySettings;
import lab.stoneshelter.services.OutboxDispatcherService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.StoneService;
import lab.stoneshelter.shared.StoneCreateRequest;
import lab.stoneshelter.shared.StoneUpdateRequest;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class StoneIndexPublicationTest extends IntegrationTest {
    @Autowired private StoneService stoneService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private OutboxDispatcherService outboxDispatcherService;
    @MockitoBean private OutboxKafkaTopicService outboxKafkaTopicService;
    @MockitoBean private StoneIndexKafkaTopicService stoneIndexKafkaTopicService;
    @MockitoBean private OllamaEmbeddingRepository ollamaEmbeddingRepository;
    private final List<Long> createdIds = new ArrayList<>();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS test_reject_index_events");
        for (long id : createdIds) {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", id);
            jdbcTemplate.update("DELETE FROM stone_index_source_version WHERE stone_id = ?", id);
            jdbcTemplate.update("DELETE FROM stone WHERE id = ?", id);
        }
    }

    @Test
    void creationWritesACompleteCommittedSnapshotAndNoSynchronousEmbedding() {
        long id = create();
        JsonNode payload = events(id).getFirst();
        assertThat(payload.get("name").asString()).isEqualTo("Luna");
        assertThat(payload.get("stoneType").asString()).isEqualTo("BASALT");
        assertThat(payload.get("stoneSize").asString()).isEqualTo("SMALL");
        assertThat(payload.get("biography").asString()).isEqualTo("A calm stone.");
        assertThat(payload.get("sourceVersion").asLong()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT delivery_state FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", String.class, id)).isEqualTo("PENDING");
        verifyNoInteractions(ollamaEmbeddingRepository);
    }

    @Test
    void excludedAndUnchangedFieldsDoNotEmitButEveryRelevantFieldDoes() {
        long id = create();
        StoneUpdateRequest stoneUpdateRequest = update();
        stoneService.update(id, stoneUpdateRequest);
        stoneUpdateRequest.setPhoto("changed.jpg");
        stoneUpdateRequest.setAdmissionDate(Instant.parse("1999-01-01T00:00:00Z"));
        stoneUpdateRequest.setAdoptionStatus(AdoptionStatus.ADOPTED);
        stoneService.update(id, stoneUpdateRequest);
        assertThat(events(id)).hasSize(1);
        stoneUpdateRequest.setName("Mars");
        stoneService.update(id, stoneUpdateRequest);
        stoneUpdateRequest.setStoneType(StoneType.GRANITE);
        stoneService.update(id, stoneUpdateRequest);
        stoneUpdateRequest.setStoneSize(StoneSize.LARGE);
        stoneService.update(id, stoneUpdateRequest);
        stoneUpdateRequest.setBiography(null);
        stoneService.update(id, stoneUpdateRequest);
        assertThat(events(id)).hasSize(5);
        assertThat(version(id)).isEqualTo(5);
        assertThat(events(id).getLast().get("biography").isNull()).isTrue();
    }

    @Test
    void deletionEmitsOnlyIdentityAndNextVersionAndKeepsHistory() {
        long id = create();
        assertThat(stoneService.delete(id).id()).isEqualTo(id);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone WHERE id = ?", Long.class, id)).isZero();
        assertThat(version(id)).isEqualTo(2);
        JsonNode payload = events(id).getLast();
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("stoneId", "sourceVersion");
        assertThat(payload.get("sourceVersion").asLong()).isEqualTo(2);
    }

    @Test
    void outboxFailureRollsBackCreateUpdateDeleteAndVersion() {
        long id = create();
        long before = jdbcTemplate.queryForObject("SELECT count(*) FROM stone", Long.class);
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT test_reject_index_events CHECK (payload->>'name' = 'Luna')");
        StoneCreateRequest stoneCreateRequest = request();
        stoneCreateRequest.setName("Rejected");
        assertThatThrownBy(() -> stoneService.create(stoneCreateRequest)).isInstanceOf(OutboxWriteException.class);
        StoneUpdateRequest stoneUpdateRequest = update();
        stoneUpdateRequest.setName("Rejected");
        assertThatThrownBy(() -> stoneService.update(id, stoneUpdateRequest)).isInstanceOf(OutboxWriteException.class);
        // A missing field evaluates to NULL in PostgreSQL CHECK; reject deletion explicitly.
        jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT test_reject_index_events");
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT test_reject_index_events CHECK (event_type <> 'STONE_CARD_INDEX_DELETED')");
        assertThatThrownBy(() -> stoneService.delete(id)).isInstanceOf(OutboxWriteException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone", Long.class)).isEqualTo(before);
        assertThat(stoneService.get(id).getName()).isEqualTo("Luna");
        assertThat(version(id)).isEqualTo(1);
        assertThat(events(id)).hasSize(1);
    }

    @Test
    void topicAndBothKeyRoutesMatchAsyncApi() {
        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        OutboxDeliverySettings outboxDeliverySettings = new OutboxDeliverySettings(new MockEnvironment());
        new StoneIndexKafkaTopicService(kafkaAdmin, outboxDeliverySettings).ensureReady();
        ArgumentCaptor<NewTopic> topic = ArgumentCaptor.forClass(NewTopic.class);
        verify(kafkaAdmin).createOrModifyTopics(topic.capture());
        assertThat(topic.getValue().name()).isEqualTo("stone-card-index-events");
        assertThat(topic.getValue().numPartitions()).isEqualTo(1);
        assertThat(topic.getValue().replicationFactor()).isEqualTo((short) 1);
        assertThat(topic.getValue().configs()).containsEntry("retention.ms", "86400000");
        for (String type : List.of("STONE_CARD_INDEX_UPDATED", "STONE_CARD_INDEX_DELETED")) {
            assertThat(outboxDeliverySettings.route(type)).isEqualTo(new OutboxDeliverySettings.Route("stone-card-index-events", "stoneId"));
        }
    }

    private long create() {
        long id = stoneService.create(request()).getId();
        createdIds.add(id);
        return id;
    }

    private StoneCreateRequest request() {
        StoneCreateRequest stoneCreateRequest = new StoneCreateRequest();
        stoneCreateRequest.setName("Luna");
        stoneCreateRequest.setStoneType(StoneType.BASALT);
        stoneCreateRequest.setStoneSize(StoneSize.SMALL);
        stoneCreateRequest.setBiography("A calm stone.");
        stoneCreateRequest.setAdoptionStatus(AdoptionStatus.AVAILABLE);
        return stoneCreateRequest;
    }

    private StoneUpdateRequest update() {
        StoneUpdateRequest stoneUpdateRequest = new StoneUpdateRequest();
        stoneUpdateRequest.setName("Luna");
        stoneUpdateRequest.setStoneType(StoneType.BASALT);
        stoneUpdateRequest.setStoneSize(StoneSize.SMALL);
        stoneUpdateRequest.setBiography("A calm stone.");
        stoneUpdateRequest.setAdoptionStatus(AdoptionStatus.AVAILABLE);
        stoneUpdateRequest.setAdmissionDate(Instant.parse("2000-01-01T00:00:00Z"));
        return stoneUpdateRequest;
    }

    private List<JsonNode> events(long id) {
        return jdbcTemplate.query("SELECT payload::text FROM outbox_event WHERE (payload->>'stoneId')::bigint = ? ORDER BY (payload->>'sourceVersion')::bigint",
                (resultSet, row) -> jsonMapper.readTree(resultSet.getString(1)), id);
    }

    private long version(long id) {
        return jdbcTemplate.queryForObject("SELECT source_version FROM stone_index_source_version WHERE stone_id = ?", Long.class, id);
    }
}
