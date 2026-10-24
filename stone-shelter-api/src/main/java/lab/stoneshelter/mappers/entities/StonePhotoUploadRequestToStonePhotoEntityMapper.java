package lab.stoneshelter.mappers.entities;

import java.time.Instant;
import lab.stoneshelter.entities.StonePhotoEntity;
import lab.stoneshelter.shared.StonePhotoUploadRequest;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoUploadRequestToStonePhotoEntityMapper
        extends AbstractEntityMapper<StonePhotoUploadRequest, StonePhotoEntity> {
    @Override
    protected StonePhotoEntity newEntity() { return new StonePhotoEntity(); }
    @Override
    protected StonePhotoEntity populateEntity(StonePhotoUploadRequest request, StonePhotoEntity stonePhotoEntity) {
        stonePhotoEntity.setAddedAt(Instant.now());
        return stonePhotoEntity;
    }
}
