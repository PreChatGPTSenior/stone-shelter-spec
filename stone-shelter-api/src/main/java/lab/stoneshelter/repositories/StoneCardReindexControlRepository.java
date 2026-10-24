package lab.stoneshelter.repositories;

import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneCardReindexControlRepository {
    private final JdbcTemplate jdbcTemplate;
    public StoneCardReindexControlRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<UUID> findActiveJobIdForUpdate() {
        return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT active_job FROM stone_card_reindex_control WHERE id = 1 FOR UPDATE", UUID.class));
    }

    public Optional<UUID> findActiveJobId() {
        return Optional.ofNullable(jdbcTemplate.queryForObject("SELECT active_job FROM stone_card_reindex_control WHERE id = 1", UUID.class));
    }

    public boolean isCreationBlockedForShare() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT creation_blocked FROM stone_card_reindex_control WHERE id = 1 FOR SHARE", Boolean.class));
    }

    public StoneCardReindexJobEntity activate(StoneCardReindexJobEntity stoneCardReindexJobEntity) {
        jdbcTemplate.update("UPDATE stone_card_reindex_control SET active_job = ?, creation_blocked = ? WHERE id = 1",
                stoneCardReindexJobEntity.getJobId(), stoneCardReindexJobEntity.getTarget() == StoneCardReindexTarget.ALL);
        return stoneCardReindexJobEntity;
    }

    public void release(UUID jobId) {
        jdbcTemplate.update("UPDATE stone_card_reindex_control SET active_job = NULL, creation_blocked = FALSE WHERE id = 1 AND active_job = ?", jobId);
    }
}
