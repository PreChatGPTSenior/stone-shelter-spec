package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lab.stoneshelter.enums.PhotoStorageBucket;
import lab.stoneshelter.exceptions.StonePhotoDraftNotFoundException;
import lab.stoneshelter.mappers.entities.StonePhotoDraftUploadRequestToStonePhotoDraftEntityMapper;
import lab.stoneshelter.mappers.dtos.StonePhotoDraftEntityToStonePhotoDraftUploadResponseMapper;
import lab.stoneshelter.mappers.dtos.StonePhotoDraftEntityToStonePhotoDraftResponseMapper;
import lab.stoneshelter.repositories.StonePhotoDraftEntityRepository;
import lab.stoneshelter.shared.StonePhotoDraftUploadRequest;
import lab.stoneshelter.shared.StonePhotoDraftUploadResponse;
import lab.stoneshelter.shared.StonePhotoDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StonePhotoDraftService {
    private final StonePhotoDraftEntityRepository repository;
    private final PhotoValidationService validation;
    private final MinioPhotoStorageService storage;
    private final PhotoCleanupService cleanup;
    private final Clock clock;
    private final StonePhotoDraftUploadRequestToStonePhotoDraftEntityMapper requestMapper;
    private final StonePhotoDraftEntityToStonePhotoDraftUploadResponseMapper uploadResponseMapper;
    private final StonePhotoDraftEntityToStonePhotoDraftResponseMapper responseMapper;

    public StonePhotoDraftService(StonePhotoDraftEntityRepository repository, PhotoValidationService validation,
            MinioPhotoStorageService storage, PhotoCleanupService cleanup, Clock clock,
            StonePhotoDraftUploadRequestToStonePhotoDraftEntityMapper requestMapper,
            StonePhotoDraftEntityToStonePhotoDraftUploadResponseMapper uploadResponseMapper,
            StonePhotoDraftEntityToStonePhotoDraftResponseMapper responseMapper) {
        this.repository = repository;
        this.validation = validation;
        this.storage = storage;
        this.cleanup = cleanup;
        this.clock = clock;
        this.requestMapper = requestMapper;
        this.uploadResponseMapper = uploadResponseMapper;
        this.responseMapper = responseMapper;
    }

    public StonePhotoDraftUploadResponse upload(StonePhotoDraftUploadRequest request) {
        byte[] bytes = validation.validate(request);
        var stonePhotoDraftEntity = requestMapper.toEntity(request);
        cleanup.recordDraftUploadIntent(stonePhotoDraftEntity.getObjectKey());
        storage.upload(PhotoStorageBucket.DRAFT, stonePhotoDraftEntity.getObjectKey(), bytes, request.declaredMediaType());
        stonePhotoDraftEntity = repository.saveAndFlush(requestMapper.toEntity(stonePhotoDraftEntity, request));
        cleanup.enqueueDraft(stonePhotoDraftEntity.getObjectKey(), stonePhotoDraftEntity.getExpiresAt());
        return uploadResponseMapper.toDto(stonePhotoDraftEntity);
    }

    public StonePhotoDraftResponse findById(UUID id) {
        var stonePhotoDraftEntity = repository.findByIdForUpdate(id).orElseThrow(StonePhotoDraftNotFoundException::new);
        if (stonePhotoDraftEntity.getStone() != null || !stonePhotoDraftEntity.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new StonePhotoDraftNotFoundException();
        }
        storage.checkObject(PhotoStorageBucket.DRAFT, stonePhotoDraftEntity.getObjectKey());
        return responseMapper.toDto(stonePhotoDraftEntity);
    }
}
