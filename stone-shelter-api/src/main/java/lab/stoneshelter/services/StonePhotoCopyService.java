package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import lab.stoneshelter.repositories.StonePhotoDraftEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StonePhotoCopyService {
    private final StoneEntityRepository stoneRepository;
    private final StonePhotoEntityRepository photoRepository;
    private final StonePhotoDraftEntityRepository draftRepository;
    private final MinioPhotoStorageService storage;
    private final PhotoCleanupService cleanup;
    private final Clock clock;

    public StonePhotoCopyService(StoneEntityRepository stoneRepository, StonePhotoEntityRepository photoRepository,
            StonePhotoDraftEntityRepository draftRepository, MinioPhotoStorageService storage, PhotoCleanupService cleanup, Clock clock) {
        this.stoneRepository = stoneRepository;
        this.photoRepository = photoRepository;
        this.draftRepository = draftRepository;
        this.storage = storage;
        this.cleanup = cleanup;
        this.clock = clock;
    }

    public void copy(long stoneId, long photoId) {
        stoneRepository.findByIdForUpdate(stoneId).orElseThrow(() -> new StoneNotFoundException(stoneId));
        var stonePhotoEntity = photoRepository.findById(photoId).orElseThrow();
        if (stonePhotoEntity.isCopyReady() || stonePhotoEntity.getDraft() == null) return;
        var stonePhotoDraftEntity = draftRepository.findByIdForUpdate(stonePhotoEntity.getDraft().getId()).orElseThrow();
        cleanup.recordUploadIntent(stonePhotoEntity.getObjectKey());
        cleanup.lockUploadIntent(stonePhotoEntity.getObjectKey());
        storage.copy(stonePhotoDraftEntity.getObjectKey(), stonePhotoEntity.getObjectKey());
        stonePhotoEntity.setCopyReady(true);
        stonePhotoEntity = photoRepository.saveAndFlush(stonePhotoEntity);
        cleanup.completeUpload(stonePhotoEntity.getObjectKey());
        cleanup.enqueueDraft(stonePhotoDraftEntity.getObjectKey(), Instant.now(clock));
    }
}
