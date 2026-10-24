package lab.stoneshelter.services;

import java.util.UUID;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.shared.StonePhotoUploadRequest;
import lab.stoneshelter.shared.StonePhotoUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional
public class StonePhotoService {
    private final StoneEntityRepository stoneRepository;
    private final PhotoValidationService validation;
    private final PhotoCleanupService cleanup;
    private final StonePhotoAttachmentService attachment;

    public StonePhotoService(StoneEntityRepository stoneRepository, PhotoValidationService validation,
            PhotoCleanupService cleanup, StonePhotoAttachmentService attachment) {
        this.stoneRepository = stoneRepository;
        this.validation = validation;
        this.cleanup = cleanup;
        this.attachment = attachment;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StonePhotoUploadResponse upload(long stoneId, StonePhotoUploadRequest request) {
        if (!stoneRepository.existsById(stoneId)) throw new StoneNotFoundException(stoneId);
        byte[] bytes = validation.validate(request);
        String objectKey = "stones/" + stoneId + "/" + UUID.randomUUID();
        cleanup.recordUploadIntent(objectKey);
        return attachment.append(stoneId, request, bytes, objectKey);
    }
}
