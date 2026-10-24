package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StonePhotoEntity;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.shared.StonePhotoUploadResponse;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoEntityToStonePhotoUploadResponseMapper
        extends AbstractDtoMapper<StonePhotoEntity, StonePhotoUploadResponse> {
    private final MinioPhotoStorageService storage;
    public StonePhotoEntityToStonePhotoUploadResponseMapper(MinioPhotoStorageService storage) { this.storage = storage; }
    @Override
    protected StonePhotoUploadResponse mapToDto(StonePhotoEntity stonePhotoEntity) {
        return new StonePhotoUploadResponse(stonePhotoEntity.getId(), storage.readUrl(stonePhotoEntity.getObjectKey()),
                stonePhotoEntity.getAddedAt(), stonePhotoEntity.getPosition());
    }
}
