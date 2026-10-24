package lab.stoneshelter;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.services.OutboxDeliverySettings;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import liquibase.integration.spring.SpringLiquibase;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxDeliveryPersistenceTest {
    @Test
    void preservesExistingRowsAndPersistsDeliveryLifecycleAcrossConnections() throws Exception {
        try (PostgreSQLContainer database = new PostgreSQLContainer(DockerImageName.parse(
                "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
                .asCompatibleSubstituteFor("postgres"))) {
            database.start();
            DataSource dataSource = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            migrate(dataSource, "0019-create-outbox-event.xml");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            UUID eventId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO outbox_event VALUES (?, 'legacy', 1, '2026-10-12T00:00:00Z',
                        '{"value":"original"}'::jsonb, '2026-10-12T00:00:00Z', 'PENDING')
                    """, eventId);
            migrate(dataSource, "0020-add-outbox-delivery.xml");
            assertThat(jdbcTemplate.queryForMap("SELECT delivery_state, attempt_count, next_attempt_at, last_error, "
                    + "published_at, dead_lettered_at FROM outbox_event WHERE event_id = ?", eventId))
                    .containsEntry("delivery_state", "PENDING").containsEntry("attempt_count", 0)
                    .containsEntry("next_attempt_at", null).containsEntry("last_error", null)
                    .containsEntry("published_at", null).containsEntry("dead_lettered_at", null);
            assertThat(jdbcTemplate.queryForObject("SELECT payload->>'value' FROM outbox_event WHERE event_id = ?", String.class, eventId))
                    .isEqualTo("original");
            jdbcTemplate.update("UPDATE outbox_event SET delivery_state = 'DEAD_LETTER_PENDING', attempt_count = 3, "
                    + "next_attempt_at = now(), last_error = 'original failure' WHERE event_id = ?", eventId);
            JdbcTemplate restartedJdbcTemplate = new JdbcTemplate(dataSource);
            assertThat(restartedJdbcTemplate.queryForMap("SELECT attempt_count, last_error FROM outbox_event WHERE event_id = ?", eventId))
                    .containsEntry("attempt_count", 3).containsEntry("last_error", "original failure");
            UUID futureEventId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO outbox_event (event_id, event_type, schema_version, occurred_at, payload, created_at, delivery_state, next_attempt_at)
                    VALUES (?, 'future', 1, now(), '{"value":1}'::jsonb, now(), 'PENDING', now() + interval '1 hour')
                    """, futureEventId);
            assertThat(restartedJdbcTemplate.queryForList("""
                    SELECT event_id FROM outbox_event
                    WHERE delivery_state IN ('PENDING', 'DEAD_LETTER_PENDING')
                        AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                    """, UUID.class)).containsExactly(eventId);
            jdbcTemplate.update("UPDATE outbox_event SET delivery_state = 'DEAD_LETTERED', dead_lettered_at = now(), next_attempt_at = NULL WHERE event_id = ?", eventId);
            assertThat(restartedJdbcTemplate.queryForObject("SELECT published_at IS NULL AND dead_lettered_at IS NOT NULL "
                    + "FROM outbox_event WHERE event_id = ?", Boolean.class, eventId)).isTrue();
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE outbox_event SET attempt_count = 4 WHERE event_id = ?", eventId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE outbox_event SET delivery_state = 'UNKNOWN' WHERE event_id = ?", eventId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbcTemplate.update("DELETE FROM outbox_event WHERE event_id IN (?, ?)", eventId, futureEventId);
        }
    }

    @Test
    void defaultsEnvironmentOverridesAndRoutesAreExplicit() {
        OutboxDeliverySettings settings = new OutboxDeliverySettings(new MockEnvironment());
        assertThat(settings.pollInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.batchSize()).isEqualTo(20);
        assertThat(settings.deadLetterRetention()).isEqualTo(Duration.ofHours(24));
        OutboxDeliverySettings configured = new OutboxDeliverySettings(new MockEnvironment()
                .withProperty("OUTBOX_POLL_INTERVAL", "PT3S").withProperty("OUTBOX_BATCH_SIZE", "7")
                .withProperty("OUTBOX_DEAD_LETTER_RETENTION", "PT48H")
                .withProperty("OUTBOX_ROUTES", "{\"event\":{\"topic\":\"events\",\"keyField\":\"stoneId\"}}"));
        assertThat(configured.pollInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(configured.batchSize()).isEqualTo(7);
        assertThat(configured.deadLetterRetention()).isEqualTo(Duration.ofHours(48));
        assertThat(configured.route("event")).isEqualTo(new OutboxDeliverySettings.Route("events", "stoneId"));
        assertThat(settings.registerRoute("registered", "registered-events", null)).isEqualTo(settings.route("registered"));
        assertThatThrownBy(() -> settings.route("unknown")).isInstanceOf(OutboxValidationException.class);
    }

    @Test
    void invalidLimitsAndRouteRepresentationAreRejected() {
        for (String value : new String[] { "0", "-1", "bad" }) {
            assertThatThrownBy(() -> new OutboxDeliverySettings(new MockEnvironment().withProperty("OUTBOX_BATCH_SIZE", value)).batchSize())
                    .isInstanceOf(OutboxValidationException.class);
        }
        for (String value : new String[] { "PT0S", "-PT1S", "bad" }) {
            assertThatThrownBy(() -> new OutboxDeliverySettings(new MockEnvironment().withProperty("OUTBOX_POLL_INTERVAL", value)).pollInterval())
                    .isInstanceOf(OutboxValidationException.class);
        }
        for (String value : new String[] { "[]", "bad", "{\"event\":{}}", "{\"event\":{\"topic\":\"events\",\"keyField\":2}}" }) {
            assertThatThrownBy(() -> new OutboxDeliverySettings(new MockEnvironment().withProperty("OUTBOX_ROUTES", value)).route("event"))
                    .isInstanceOf(OutboxValidationException.class);
        }
    }

    @Test
    void explicitlyProvisionsDlqWithConfiguredRetentionAndRetriesFailedProvisioning() {
        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        OutboxKafkaTopicService service = new OutboxKafkaTopicService(kafkaAdmin, new OutboxDeliverySettings(new MockEnvironment()
                .withProperty("OUTBOX_DEAD_LETTER_RETENTION", "PT48H")));
        doThrow(new KafkaException("broker down")).doNothing().when(kafkaAdmin).createOrModifyTopics(any(NewTopic.class));
        assertThat(service.ensureReady()).isFalse();
        assertThat(service.ensureReady()).isTrue();
        assertThat(service.ensureReady()).isTrue();
        ArgumentCaptor<NewTopic> topic = ArgumentCaptor.forClass(NewTopic.class);
        verify(kafkaAdmin, times(2)).createOrModifyTopics(topic.capture());
        assertThat(topic.getValue().name()).isEqualTo("outbox-dead-letter");
        assertThat(topic.getValue().configs()).containsAllEntriesOf(Map.of("retention.ms", "172800000", "cleanup.policy", "delete"));
        service.invalidate();
        assertThat(service.ensureReady()).isTrue();
        verify(kafkaAdmin, times(3)).createOrModifyTopics(any(NewTopic.class));
    }

    private void migrate(DataSource dataSource, String filename) throws Exception {
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/" + filename);
        springLiquibase.afterPropertiesSet();
    }
}
