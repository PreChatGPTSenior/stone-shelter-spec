package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StoneCardReindexItemEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneCardReindexItemRepository {
    private final JdbcTemplate jdbcTemplate;
    public StoneCardReindexItemRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public StoneCardReindexItemEntity save(StoneCardReindexItemEntity stoneCardReindexItemEntity) {
        jdbcTemplate.update("""
                INSERT INTO stone_card_reindex_item (job_id, stone_id, work_kind, outcome, error) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (job_id, stone_id, work_kind) DO UPDATE SET outcome = EXCLUDED.outcome, error = EXCLUDED.error
                """, stoneCardReindexItemEntity.getJobId(), stoneCardReindexItemEntity.getStoneId(),
                stoneCardReindexItemEntity.getWorkKind(), stoneCardReindexItemEntity.getOutcome(), stoneCardReindexItemEntity.getError());
        return findById(stoneCardReindexItemEntity, false).orElseThrow();
    }

    public Optional<StoneCardReindexItemEntity> findByIdForUpdate(StoneCardReindexItemEntity stoneCardReindexItemEntity) {
        return findById(stoneCardReindexItemEntity, true);
    }

    public List<StoneCardReindexItemEntity> findPending(UUID jobId, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM stone_card_reindex_item WHERE job_id = ? AND outcome = 'PENDING'
                ORDER BY stone_id, work_kind LIMIT ?
                """, this::mapEntity, jobId, limit);
    }

    private Optional<StoneCardReindexItemEntity> findById(StoneCardReindexItemEntity stoneCardReindexItemEntity, boolean lock) {
        return jdbcTemplate.query("""
                SELECT * FROM stone_card_reindex_item WHERE job_id = ? AND stone_id = ? AND work_kind = ?
                """ + (lock ? " FOR UPDATE" : ""), this::mapEntity, stoneCardReindexItemEntity.getJobId(),
                stoneCardReindexItemEntity.getStoneId(), stoneCardReindexItemEntity.getWorkKind()).stream().findFirst();
    }

    private StoneCardReindexItemEntity mapEntity(ResultSet resultSet, int row) throws SQLException {
        StoneCardReindexItemEntity stoneCardReindexItemEntity = new StoneCardReindexItemEntity();
        stoneCardReindexItemEntity.setJobId(resultSet.getObject("job_id", UUID.class));
        stoneCardReindexItemEntity.setStoneId(resultSet.getLong("stone_id"));
        stoneCardReindexItemEntity.setWorkKind(resultSet.getString("work_kind"));
        stoneCardReindexItemEntity.setOutcome(resultSet.getString("outcome"));
        stoneCardReindexItemEntity.setError(resultSet.getString("error"));
        return stoneCardReindexItemEntity;
    }
}
