package lab.stoneshelter.repositories;

import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.AdoptionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true, timeout = 5)
public class StoneRagKnowledgeRepository {
    private final JdbcTemplate jdbcTemplate;
    public StoneRagKnowledgeRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    public Optional<StoneEntity> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM stone WHERE id=? AND adoption_status='AVAILABLE'", (row, index) -> {
            StoneEntity stone = new StoneEntity(); stone.setId(row.getLong("id")); stone.setName(row.getString("name"));
            stone.setStorageUuid(row.getObject("storage_uuid", UUID.class)); stone.setPhoto(row.getString("photo_url"));
            stone.setStoneType(StoneType.valueOf(row.getString("stone_type"))); stone.setStoneSize(StoneSize.valueOf(row.getString("size")));
            stone.setAdoptionStatus(AdoptionStatus.valueOf(row.getString("adoption_status"))); stone.setBiography(row.getString("biography"));
            stone.setAdmissionDate(row.getTimestamp("admission_date").toInstant()); return stone;
        }, id).stream().findFirst();
    }
    public void probe() { jdbcTemplate.queryForObject("SELECT count(*) FROM stone", Long.class); }
}
