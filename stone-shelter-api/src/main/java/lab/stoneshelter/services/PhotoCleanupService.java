package lab.stoneshelter.services;

import java.time.Instant;
import java.time.Clock;
import java.util.List;
import lab.stoneshelter.entities.PhotoCleanupEntity;
import lab.stoneshelter.entities.PhotoCleanupId;
import lab.stoneshelter.enums.PhotoStorageBucket;
import lab.stoneshelter.repositories.PhotoCleanupEntityRepository;
import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import lab.stoneshelter.repositories.StonePhotoDraftEntityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PhotoCleanupService {
    private final PhotoCleanupEntityRepository cleanupRepository;
    private final StonePhotoEntityRepository photoRepository;
    private final MinioPhotoStorageService storage;
    private final StonePhotoDraftEntityRepository draftRepository;
    private final Clock clock;

    public PhotoCleanupService(PhotoCleanupEntityRepository cleanupRepository,
            StonePhotoEntityRepository photoRepository, MinioPhotoStorageService storage,
            StonePhotoDraftEntityRepository draftRepository, Clock clock) {
        this.cleanupRepository = cleanupRepository;
        this.photoRepository = photoRepository;
        this.storage = storage;
        this.draftRepository = draftRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUploadIntent(String objectKey) {
        enqueue(objectKey, Instant.now(clock).plusSeconds(86400));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDraftUploadIntent(String objectKey) {
        enqueueDraft(objectKey, Instant.now(clock).plusSeconds(86400));
    }

    public void enqueueDraft(String objectKey, Instant availableAt) {
        var photoCleanupEntity = new PhotoCleanupEntity();
        photoCleanupEntity.setObjectKey(objectKey);
        photoCleanupEntity.setBucketType(PhotoStorageBucket.DRAFT);
        photoCleanupEntity.setAvailableAt(availableAt);
        cleanupRepository.save(photoCleanupEntity);
    }

    public void enqueueDeletedPhotos(List<String> objectKeys) {
        objectKeys.forEach(objectKey -> enqueue(objectKey, Instant.now(clock)));
    }

    public void lockUploadIntent(String objectKey) {
        cleanupRepository.findByObjectKeyForUpdate(objectKey).orElseThrow();
    }

    public void completeUpload(String objectKey) {
        cleanupRepository.deleteById(new PhotoCleanupId(objectKey, PhotoStorageBucket.PERMANENT));
    }

    @Transactional(readOnly = true, timeout = 10)
    public List<String> findDueKeys(int limit) {
        return cleanupRepository.findByAvailableAtBeforeOrderByAvailableAtAsc(Instant.now(clock), PageRequest.of(0, limit))
                .stream().map(PhotoCleanupEntity::getObjectKey).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void clean(String objectKey) {
        var photoCleanupEntity = cleanupRepository.findByObjectKeyForUpdate(objectKey).orElse(null);
        if (photoCleanupEntity == null || photoCleanupEntity.getAvailableAt().isAfter(Instant.now(clock))) return;
        if (photoRepository.existsByObjectKeyAndCopyReadyTrue(objectKey)) {
            // A durable stale intent must never remove a successfully attached object.
            cleanupRepository.delete(photoCleanupEntity);
            return;
        }
        storage.remove(objectKey);
        cleanupRepository.delete(photoCleanupEntity);
    }

    @Transactional(readOnly = true, timeout = 10)
    public List<String> findDueDraftKeys(int limit) {
        return cleanupRepository.findByBucketTypeAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                PhotoStorageBucket.DRAFT, Instant.now(clock), PageRequest.of(0, limit))
                .stream().map(PhotoCleanupEntity::getObjectKey).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void cleanDraft(String objectKey) {
        var stonePhotoDraftEntity = draftRepository.findByObjectKeyForUpdate(objectKey).orElse(null);
        var photoCleanupEntity = cleanupRepository.findByObjectKeyAndBucketTypeForUpdate(objectKey, PhotoStorageBucket.DRAFT).orElse(null);
        if (photoCleanupEntity == null || photoCleanupEntity.getAvailableAt().isAfter(Instant.now(clock))) return;
        if (stonePhotoDraftEntity != null) {
            if (stonePhotoDraftEntity.getStone() != null) {
                if (!photoRepository.existsByDraftIdAndCopyReadyTrue(stonePhotoDraftEntity.getId())) {
                    // Associated sources are retained indefinitely after a failed copy.
                    cleanupRepository.delete(photoCleanupEntity);
                    return;
                }
            } else if (stonePhotoDraftEntity.getExpiresAt().isAfter(Instant.now(clock))) {
                photoCleanupEntity.setAvailableAt(stonePhotoDraftEntity.getExpiresAt());
                return;
            }
        }
        storage.remove(PhotoStorageBucket.DRAFT, objectKey);
        if (stonePhotoDraftEntity != null && stonePhotoDraftEntity.getStone() == null) {
            draftRepository.delete(stonePhotoDraftEntity);
        }
        cleanupRepository.delete(photoCleanupEntity);
    }

    private void enqueue(String objectKey, Instant availableAt) {
        var photoCleanupEntity = new PhotoCleanupEntity();
        photoCleanupEntity.setObjectKey(objectKey);
        photoCleanupEntity.setAvailableAt(availableAt);
        cleanupRepository.save(photoCleanupEntity);
    }
}
