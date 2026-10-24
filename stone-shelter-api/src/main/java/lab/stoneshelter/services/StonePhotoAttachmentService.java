package lab.stoneshelter.services;

import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.mappers.dtos.StonePhotoEntityToStonePhotoUploadResponseMapper;
import lab.stoneshelter.mappers.entities.StonePhotoUploadRequestToStonePhotoEntityMapper;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import lab.stoneshelter.shared.StonePhotoUploadRequest;
import lab.stoneshelter.shared.StonePhotoUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StonePhotoAttachmentService {
    private final StoneEntityRepository stoneRepository;
    private final StonePhotoEntityRepository photoRepository;
    private final MinioPhotoStorageService storage;
    private final PhotoCleanupService cleanup;
    private final StonePhotoUploadRequestToStonePhotoEntityMapper requestMapper;
    private final StonePhotoEntityToStonePhotoUploadResponseMapper responseMapper;

    public StonePhotoAttachmentService(StoneEntityRepository stoneRepository, StonePhotoEntityRepository photoRepository,
            MinioPhotoStorageService storage, PhotoCleanupService cleanup,
            StonePhotoUploadRequestToStonePhotoEntityMapper requestMapper,
            StonePhotoEntityToStonePhotoUploadResponseMapper responseMapper) {
        this.stoneRepository = stoneRepository;
        this.photoRepository = photoRepository;
        this.storage = storage;
        this.cleanup = cleanup;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    public StonePhotoUploadResponse append(long stoneId, StonePhotoUploadRequest request, byte[] bytes, String objectKey) {
        var stoneEntity = stoneRepository.findByIdForUpdate(stoneId).orElseThrow(() -> new StoneNotFoundException(stoneId));
        cleanup.lockUploadIntent(objectKey);
        storage.upload(objectKey, bytes, request.declaredMediaType());
        var stonePhotoEntity = requestMapper.toEntity(request);
        stonePhotoEntity.setStone(stoneEntity);
        stonePhotoEntity.setObjectKey(objectKey);
        stonePhotoEntity.setPosition(photoRepository.nextPosition(stoneId));
        var stonePhotoUploadResponse = responseMapper.toDto(photoRepository.saveAndFlush(stonePhotoEntity));
        cleanup.completeUpload(objectKey);
        return stonePhotoUploadResponse;
    }
}
