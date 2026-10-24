package lab.stoneshelter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneReservationEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.repositories.StoneReservationEntityRepository;
import lab.stoneshelter.services.StoneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StoneReservationPersistenceTest extends IntegrationTest {
    @Autowired private StoneEntityRepository stoneEntityRepository;
    @Autowired private StoneReservationEntityRepository stoneReservationEntityRepository;
    @Autowired private StoneService stoneService;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final List<Long> stoneIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (long id : stoneIds) {
            jdbcTemplate.update("delete from stone where id = ?", id);
        }
    }

    @Test
    void enforcesOneReservationPerStoneAndPreservesArbitraryText() {
        var stoneEntity = createStone();
        var stoneReservationEntity = stoneReservationEntityRepository.saveAndFlush(reservation(stoneEntity));
        assertThat(jdbcTemplate.queryForObject("select applicant_name from stone_reservation where id = ?",
                String.class, stoneReservationEntity.getId())).isEqualTo("  Visitor 石  ");
        assertThat(jdbcTemplate.queryForObject("select contact_details from stone_reservation where id = ?",
                String.class, stoneReservationEntity.getId())).isEqualTo("any contact " + "x".repeat(3000));
        assertThatThrownBy(() -> stoneReservationEntityRepository.saveAndFlush(reservation(stoneEntity)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from stone_reservation where stone_id = ?",
                Long.class, stoneEntity.getId())).isEqualTo(1);
    }

    @Test
    void existingStoneDeletionRemovesDependentReservation() {
        var stoneEntity = createStone();
        stoneReservationEntityRepository.saveAndFlush(reservation(stoneEntity));
        assertThat(stoneService.delete(stoneEntity.getId()).id()).isEqualTo(stoneEntity.getId());
        assertThat(stoneReservationEntityRepository.existsByStoneId(stoneEntity.getId())).isFalse();
    }

    private StoneEntity createStone() {
        var stoneEntity = new StoneEntity();
        stoneEntity.setName("Reservation persistence test");
        stoneEntity.setStoneType(StoneType.values()[0]);
        stoneEntity.setStoneSize(StoneSize.SMALL);
        stoneEntity.setAdoptionStatus(AdoptionStatus.RESERVED);
        stoneEntity.setAdmissionDate(Instant.parse("2026-01-01T00:00:00Z"));
        stoneEntity = stoneEntityRepository.saveAndFlush(stoneEntity);
        stoneIds.add(stoneEntity.getId());
        return stoneEntity;
    }

    private StoneReservationEntity reservation(StoneEntity stoneEntity) {
        var stoneReservationEntity = new StoneReservationEntity();
        stoneReservationEntity.setStone(stoneEntity);
        stoneReservationEntity.setApplicantName("  Visitor 石  ");
        stoneReservationEntity.setContactDetails("any contact " + "x".repeat(3000));
        stoneReservationEntity.setCreatedAt(Instant.now());
        return stoneReservationEntity;
    }
}
