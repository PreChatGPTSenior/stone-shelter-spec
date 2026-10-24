package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class OutboxEventRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OutboxEventRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public OutboxEventEntity save(OutboxEventEntity outboxEventEntity) {
        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, event_type, schema_version, occurred_at, payload, created_at, delivery_state)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, outboxEventEntity.getEventId(), outboxEventEntity.getEventType(), outboxEventEntity.getSchemaVersion(),
                OffsetDateTime.ofInstant(outboxEventEntity.getOccurredAt(), ZoneOffset.UTC),
                jsonMapper.writeValueAsString(outboxEventEntity.getPayload()),
                OffsetDateTime.ofInstant(outboxEventEntity.getCreatedAt(), ZoneOffset.UTC),
                outboxEventEntity.getDeliveryState().name());
        return findById(outboxEventEntity.getEventId())
                .orElseThrow(() -> new IllegalStateException("The persisted outbox event is missing."));
    }

    public Optional<OutboxEventEntity> findById(UUID eventId) {
        return jdbcTemplate.query("""
                SELECT event_id, event_type, schema_version, occurred_at, payload, created_at, delivery_state,
                    attempt_count, next_attempt_at, last_error, published_at, dead_lettered_at
                FROM outbox_event WHERE event_id = ?
                """, this::mapEntity, eventId).stream().findFirst();
    }

    public List<OutboxEventEntity> findEligible(Instant now, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id, event_type, schema_version, occurred_at, payload, created_at, delivery_state,
                    attempt_count, next_attempt_at, last_error, published_at, dead_lettered_at
                FROM outbox_event
                WHERE delivery_state IN ('PENDING', 'DEAD_LETTER_PENDING')
                    AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY created_at, event_id LIMIT ?
                """, this::mapEntity, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), limit);
    }

    public OutboxEventEntity markPublished(UUID eventId, Instant now) {
        jdbcTemplate.update("""
                UPDATE outbox_event SET delivery_state = 'PUBLISHED', attempt_count = attempt_count + 1,
                    published_at = ?, next_attempt_at = NULL
                WHERE event_id = ? AND delivery_state = 'PENDING'
                """, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), eventId);
        return findById(eventId).orElseThrow();
    }

    public OutboxEventEntity recordFailure(OutboxEventEntity outboxEventEntity, String error, Instant nextAttemptAt) {
        jdbcTemplate.update("""
                UPDATE outbox_event SET attempt_count = attempt_count + 1,
                    delivery_state = CASE WHEN attempt_count + 1 >= 3 THEN 'DEAD_LETTER_PENDING' ELSE 'PENDING' END,
                    last_error = ?, next_attempt_at = ?
                WHERE event_id = ? AND delivery_state = 'PENDING' AND attempt_count = ?
                """, error, OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC),
                outboxEventEntity.getEventId(), outboxEventEntity.getAttemptCount());
        return findById(outboxEventEntity.getEventId()).orElseThrow();
    }

    public OutboxEventEntity markDeadLettered(UUID eventId, Instant now) {
        jdbcTemplate.update("""
                UPDATE outbox_event SET delivery_state = 'DEAD_LETTERED', dead_lettered_at = ?, next_attempt_at = NULL
                WHERE event_id = ? AND delivery_state = 'DEAD_LETTER_PENDING'
                """, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), eventId);
        return findById(eventId).orElseThrow();
    }

    public OutboxEventEntity deferDeadLetter(UUID eventId, Instant nextAttemptAt) {
        jdbcTemplate.update("""
                UPDATE outbox_event SET next_attempt_at = ?
                WHERE event_id = ? AND delivery_state = 'DEAD_LETTER_PENDING'
                """, OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC), eventId);
        return findById(eventId).orElseThrow();
    }

    private OutboxEventEntity mapEntity(ResultSet resultSet, int row) throws SQLException {
        OutboxEventEntity outboxEventEntity = new OutboxEventEntity();
        outboxEventEntity.setEventId(resultSet.getObject("event_id", UUID.class));
        outboxEventEntity.setEventType(resultSet.getString("event_type"));
        outboxEventEntity.setSchemaVersion(resultSet.getInt("schema_version"));
        outboxEventEntity.setOccurredAt(resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant());
        outboxEventEntity.setPayload(jsonMapper.readTree(resultSet.getString("payload")));
        outboxEventEntity.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
        outboxEventEntity.setDeliveryState(OutboxDeliveryState.valueOf(resultSet.getString("delivery_state")));
        outboxEventEntity.setAttemptCount(resultSet.getInt("attempt_count"));
        outboxEventEntity.setNextAttemptAt(instant(resultSet, "next_attempt_at"));
        outboxEventEntity.setLastError(resultSet.getString("last_error"));
        outboxEventEntity.setPublishedAt(instant(resultSet, "published_at"));
        outboxEventEntity.setDeadLetteredAt(instant(resultSet, "dead_lettered_at"));
        return outboxEventEntity;
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime offsetDateTime = resultSet.getObject(column, OffsetDateTime.class);
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
