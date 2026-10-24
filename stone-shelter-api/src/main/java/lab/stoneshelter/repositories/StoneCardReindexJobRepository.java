package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.entities.StoneCardReindexItemEntity;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.enums.StoneCardReindexStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneCardReindexJobRepository {
    private final JdbcTemplate jdbcTemplate;
    public StoneCardReindexJobRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public StoneCardReindexJobEntity save(StoneCardReindexJobEntity stoneCardReindexJobEntity) {
        jdbcTemplate.update("""
                INSERT INTO stone_card_reindex_job (job_id, target, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (job_id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                """, stoneCardReindexJobEntity.getJobId(), stoneCardReindexJobEntity.getTarget().name(),
                stoneCardReindexJobEntity.getStatus().name(),
                OffsetDateTime.ofInstant(stoneCardReindexJobEntity.getCreatedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(stoneCardReindexJobEntity.getUpdatedAt(), ZoneOffset.UTC));
        return findById(stoneCardReindexJobEntity.getJobId()).orElseThrow();
    }

    public Optional<StoneCardReindexJobEntity> findById(UUID jobId) {
        Optional<StoneCardReindexJobEntity> stoneCardReindexJobEntity = jdbcTemplate.query("""
                SELECT j.*, COUNT(i.stone_id) AS total_count,
                    COUNT(i.stone_id) FILTER (WHERE i.outcome <> 'PENDING') AS processed_count,
                    COUNT(i.stone_id) FILTER (WHERE i.outcome IN ('ENQUEUED', 'RECONCILED')) AS enqueued_count,
                    COUNT(i.stone_id) FILTER (WHERE i.outcome = 'SKIPPED') AS skipped_count,
                    COUNT(i.stone_id) FILTER (WHERE i.outcome = 'FAILED') AS failed_count,
                    COUNT(i.stone_id) FILTER (WHERE i.outcome = 'RECONCILED') AS reconciled_count
                FROM stone_card_reindex_job j LEFT JOIN stone_card_reindex_item i ON i.job_id = j.job_id
                WHERE j.job_id = ? GROUP BY j.job_id
                """, this::mapEntity, jobId).stream().findFirst();
        stoneCardReindexJobEntity.ifPresent(job -> job.setErrors(jdbcTemplate.query(
                "SELECT stone_id, work_kind, outcome, error FROM stone_card_reindex_item WHERE job_id = ? AND outcome = 'FAILED' ORDER BY stone_id",
                (resultSet, row) -> {
                    StoneCardReindexItemEntity item = new StoneCardReindexItemEntity();
                    item.setJobId(jobId);
                    item.setStoneId(resultSet.getLong("stone_id"));
                    item.setWorkKind(resultSet.getString("work_kind"));
                    item.setOutcome(resultSet.getString("outcome"));
                    item.setError(resultSet.getString("error"));
                    return item;
                }, jobId)));
        return stoneCardReindexJobEntity;
    }

    private StoneCardReindexJobEntity mapEntity(ResultSet resultSet, int row) throws SQLException {
        StoneCardReindexJobEntity stoneCardReindexJobEntity = new StoneCardReindexJobEntity();
        stoneCardReindexJobEntity.setJobId(resultSet.getObject("job_id", UUID.class));
        stoneCardReindexJobEntity.setTarget(StoneCardReindexTarget.valueOf(resultSet.getString("target")));
        stoneCardReindexJobEntity.setStatus(StoneCardReindexStatus.valueOf(resultSet.getString("status")));
        stoneCardReindexJobEntity.setTotalCount(resultSet.getLong("total_count"));
        stoneCardReindexJobEntity.setProcessedCount(resultSet.getLong("processed_count"));
        stoneCardReindexJobEntity.setEnqueuedCount(resultSet.getLong("enqueued_count"));
        stoneCardReindexJobEntity.setSkippedCount(resultSet.getLong("skipped_count"));
        stoneCardReindexJobEntity.setFailedCount(resultSet.getLong("failed_count"));
        stoneCardReindexJobEntity.setReconciledCount(resultSet.getLong("reconciled_count"));
        stoneCardReindexJobEntity.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
        stoneCardReindexJobEntity.setUpdatedAt(resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
        return stoneCardReindexJobEntity;
    }
}
