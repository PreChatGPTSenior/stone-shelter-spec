package lab.stoneshelter.repositories;

import java.util.List;
import lab.stoneshelter.entities.StonePhotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StonePhotoEntityRepository extends JpaRepository<StonePhotoEntity, Long> {
    List<StonePhotoEntity> findByStoneIdOrderByPositionAsc(long stoneId);
    boolean existsByObjectKey(String objectKey);
    boolean existsByObjectKeyAndCopyReadyTrue(String objectKey);
    boolean existsByDraftIdAndCopyReadyTrue(java.util.UUID draftId);
    @Query("select p.id from StonePhotoEntity p where p.stone.id = :stoneId order by p.position")
    List<Long> findIdsByStoneId(@Param("stoneId") long stoneId);
    @Query("select coalesce(max(p.position), -1) + 1 from StonePhotoEntity p where p.stone.id = :stoneId")
    int nextPosition(@Param("stoneId") long stoneId);
}
