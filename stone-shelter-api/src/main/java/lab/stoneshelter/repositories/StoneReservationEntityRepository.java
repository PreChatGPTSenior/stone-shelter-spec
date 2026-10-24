package lab.stoneshelter.repositories;

import lab.stoneshelter.entities.StoneReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoneReservationEntityRepository extends JpaRepository<StoneReservationEntity, Long> {
    boolean existsByStoneId(long stoneId);
}
