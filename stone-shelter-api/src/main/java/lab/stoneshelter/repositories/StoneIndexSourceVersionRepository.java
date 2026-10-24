package lab.stoneshelter.repositories;

import java.util.Optional;
import lab.stoneshelter.entities.StoneIndexSourceVersionEntity;
import lab.stoneshelter.exceptions.OutboxWriteException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneIndexSourceVersionRepository {
    private final JdbcTemplate jdbcTemplate;

    public StoneIndexSourceVersionRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public StoneIndexSourceVersionEntity increment(StoneIndexSourceVersionEntity stoneIndexSourceVersionEntity) {
        try {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO stone_index_source_version (stone_id, source_version) VALUES (?, ? + 1)
                    ON CONFLICT (stone_id) DO UPDATE SET source_version = GREATEST(stone_index_source_version.source_version, ?) + 1
                    RETURNING stone_id, source_version
                    """, (resultSet, row) -> new StoneIndexSourceVersionEntity(resultSet.getLong(1), resultSet.getLong(2)),
                    stoneIndexSourceVersionEntity.stoneId(), stoneIndexSourceVersionEntity.sourceVersion(), stoneIndexSourceVersionEntity.sourceVersion());
        } catch (DataAccessException exception) {
            throw new OutboxWriteException("Cannot allocate the stone index source version.", exception);
        }
    }

    public Optional<StoneIndexSourceVersionEntity> findById(long stoneId) {
        return jdbcTemplate.query("SELECT stone_id, source_version FROM stone_index_source_version WHERE stone_id = ?",
                (resultSet, row) -> new StoneIndexSourceVersionEntity(resultSet.getLong(1), resultSet.getLong(2)), stoneId).stream().findFirst();
    }
}
