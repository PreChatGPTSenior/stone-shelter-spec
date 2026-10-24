package lab.stoneshelter.repositories;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lab.stoneshelter.entities.PhotoCleanupEntity;
import lab.stoneshelter.entities.PhotoCleanupId;
import lab.stoneshelter.enums.PhotoStorageBucket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoCleanupEntityRepository extends JpaRepository<PhotoCleanupEntity, PhotoCleanupId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhotoCleanupEntity c where c.objectKey = :objectKey and c.bucketType = lab.stoneshelter.enums.PhotoStorageBucket.PERMANENT")
    Optional<PhotoCleanupEntity> findByObjectKeyForUpdate(@Param("objectKey") String objectKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhotoCleanupEntity c where c.objectKey = :objectKey and c.bucketType = :bucketType")
    Optional<PhotoCleanupEntity> findByObjectKeyAndBucketTypeForUpdate(@Param("objectKey") String objectKey,
            @Param("bucketType") PhotoStorageBucket bucketType);
    @Query("select c from PhotoCleanupEntity c where c.availableAt <= :instant and c.bucketType = lab.stoneshelter.enums.PhotoStorageBucket.PERMANENT order by c.availableAt asc")
    List<PhotoCleanupEntity> findByAvailableAtBeforeOrderByAvailableAtAsc(@Param("instant") Instant instant, Pageable pageable);
    List<PhotoCleanupEntity> findByBucketTypeAndAvailableAtLessThanEqualOrderByAvailableAtAsc(PhotoStorageBucket bucketType, Instant instant, Pageable pageable);
}
