package lab.stoneshelter.repositories;

import java.util.Optional;
import lab.stoneshelter.entities.StoneCardIndexStateEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneCardIndexStateRepository {
    private final JdbcTemplate jdbcTemplate;
    public StoneCardIndexStateRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<StoneCardIndexStateEntity> findById(long stoneId) {
        return jdbcTemplate.query("SELECT stone_id, source_version, deleted FROM stone_card_index_state WHERE stone_id = ?",
                (resultSet, row) -> new StoneCardIndexStateEntity(resultSet.getLong(1), resultSet.getLong(2), resultSet.getBoolean(3)),
                stoneId).stream().findFirst();
    }

    public StoneCardIndexStateEntity findByIdForUpdate(long stoneId) {
        jdbcTemplate.update("INSERT INTO stone_card_index_state VALUES (?, 0, FALSE) ON CONFLICT (stone_id) DO NOTHING", stoneId);
        return jdbcTemplate.queryForObject("SELECT stone_id, source_version, deleted FROM stone_card_index_state WHERE stone_id = ? FOR UPDATE",
                (resultSet, row) -> new StoneCardIndexStateEntity(resultSet.getLong(1), resultSet.getLong(2), resultSet.getBoolean(3)), stoneId);
    }

    public StoneCardIndexStateEntity save(StoneCardIndexStateEntity stoneCardIndexStateEntity) {
        jdbcTemplate.update("UPDATE stone_card_index_state SET source_version = ?, deleted = ? WHERE stone_id = ?",
                stoneCardIndexStateEntity.sourceVersion(), stoneCardIndexStateEntity.deleted(), stoneCardIndexStateEntity.stoneId());
        return findById(stoneCardIndexStateEntity.stoneId()).orElseThrow();
    }
}
