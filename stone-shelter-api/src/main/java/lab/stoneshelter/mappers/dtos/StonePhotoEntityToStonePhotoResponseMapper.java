package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StonePhotoEntity;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.shared.StonePhotoResponse;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoEntityToStonePhotoResponseMapper
        extends AbstractDtoMapper<StonePhotoEntity, StonePhotoResponse> {
    private final MinioPhotoStorageService storage;
    public StonePhotoEntityToStonePhotoResponseMapper(MinioPhotoStorageService storage) { this.storage = storage; }
    @Override
    protected StonePhotoResponse mapToDto(StonePhotoEntity stonePhotoEntity) {
        return new StonePhotoResponse(stonePhotoEntity.getId(), stonePhotoEntity.isCopyReady() ? storage.readUrl(stonePhotoEntity.getObjectKey()) : storage.placeholderUrl(),
                stonePhotoEntity.getAddedAt(), stonePhotoEntity.getPosition());
    }
}
