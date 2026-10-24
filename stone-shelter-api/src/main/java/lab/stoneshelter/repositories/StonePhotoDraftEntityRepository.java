package lab.stoneshelter.repositories;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StonePhotoDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StonePhotoDraftEntityRepository extends JpaRepository<StonePhotoDraftEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StonePhotoDraftEntity d where d.id = :id")
    Optional<StonePhotoDraftEntity> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StonePhotoDraftEntity d where d.objectKey = :objectKey")
    Optional<StonePhotoDraftEntity> findByObjectKeyForUpdate(@Param("objectKey") String objectKey);
    List<StonePhotoDraftEntity> findByStoneId(long stoneId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StonePhotoDraftEntity d where d.stone.id = :stoneId order by d.id")
    List<StonePhotoDraftEntity> findByStoneIdForUpdate(@Param("stoneId") long stoneId);
}
