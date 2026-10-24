package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.time.ZoneOffset;
import lab.stoneshelter.entities.StoneCardIndexAttemptEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneCardIndexAttemptRepository {
    private final JdbcTemplate jdbcTemplate;
    public StoneCardIndexAttemptRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<StoneCardIndexAttemptEntity> findById(String topic, int partition, long offset) {
        return jdbcTemplate.query("SELECT * FROM stone_card_index_attempt WHERE source_topic = ? AND source_partition = ? AND source_offset = ?",
                this::mapEntity, topic, partition, offset).stream().findFirst();
    }

    public StoneCardIndexAttemptEntity save(StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity) {
        jdbcTemplate.update("""
                INSERT INTO stone_card_index_attempt (source_topic, source_partition, source_offset,
                    attempt_count, next_attempt_at, last_error, original_record, dead_letter_pending, dead_lettered)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_topic, source_partition, source_offset) DO UPDATE SET
                    attempt_count = EXCLUDED.attempt_count, next_attempt_at = EXCLUDED.next_attempt_at,
                    last_error = EXCLUDED.last_error, original_record = EXCLUDED.original_record,
                    dead_letter_pending = EXCLUDED.dead_letter_pending, dead_lettered = EXCLUDED.dead_lettered
                """, stoneCardIndexAttemptEntity.getSourceTopic(), stoneCardIndexAttemptEntity.getSourcePartition(),
                stoneCardIndexAttemptEntity.getSourceOffset(), stoneCardIndexAttemptEntity.getAttemptCount(),
                stoneCardIndexAttemptEntity.getNextAttemptAt() == null ? null :
                        OffsetDateTime.ofInstant(stoneCardIndexAttemptEntity.getNextAttemptAt(), java.time.ZoneOffset.UTC),
                stoneCardIndexAttemptEntity.getLastError(), stoneCardIndexAttemptEntity.getOriginalRecord(),
                stoneCardIndexAttemptEntity.getDeadLetterPending(), stoneCardIndexAttemptEntity.getDeadLettered());
        return findById(stoneCardIndexAttemptEntity.getSourceTopic(), stoneCardIndexAttemptEntity.getSourcePartition(),
                stoneCardIndexAttemptEntity.getSourceOffset()).orElseThrow();
    }

    public List<StoneCardIndexAttemptEntity> findPendingDeadLetters(Instant now) {
        return jdbcTemplate.query("""
                SELECT * FROM stone_card_index_attempt WHERE dead_letter_pending = TRUE
                AND (next_attempt_at IS NULL OR next_attempt_at <= ?) ORDER BY source_topic, source_partition, source_offset LIMIT 20
                """, this::mapEntity, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    public void deleteById(String topic, int partition, long offset) {
        jdbcTemplate.update("DELETE FROM stone_card_index_attempt WHERE source_topic = ? AND source_partition = ? AND source_offset = ?",
                topic, partition, offset);
    }

    private StoneCardIndexAttemptEntity mapEntity(ResultSet resultSet, int row) throws SQLException {
        StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity = new StoneCardIndexAttemptEntity();
        stoneCardIndexAttemptEntity.setSourceTopic(resultSet.getString("source_topic"));
        stoneCardIndexAttemptEntity.setSourcePartition(resultSet.getInt("source_partition"));
        stoneCardIndexAttemptEntity.setSourceOffset(resultSet.getLong("source_offset"));
        stoneCardIndexAttemptEntity.setAttemptCount(resultSet.getInt("attempt_count"));
        OffsetDateTime nextAttemptAt = resultSet.getObject("next_attempt_at", OffsetDateTime.class);
        stoneCardIndexAttemptEntity.setNextAttemptAt(nextAttemptAt == null ? null : nextAttemptAt.toInstant());
        stoneCardIndexAttemptEntity.setLastError(resultSet.getString("last_error"));
        stoneCardIndexAttemptEntity.setOriginalRecord(resultSet.getBytes("original_record"));
        stoneCardIndexAttemptEntity.setDeadLetterPending(resultSet.getBoolean("dead_letter_pending"));
        stoneCardIndexAttemptEntity.setDeadLettered(resultSet.getBoolean("dead_lettered"));
        return stoneCardIndexAttemptEntity;
    }
}
