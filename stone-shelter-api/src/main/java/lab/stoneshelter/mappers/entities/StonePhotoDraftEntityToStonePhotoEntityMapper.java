package lab.stoneshelter.mappers.entities;

import java.time.Clock;
import java.time.Instant;
import lab.stoneshelter.entities.StonePhotoDraftEntity;
import lab.stoneshelter.entities.StonePhotoEntity;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoDraftEntityToStonePhotoEntityMapper
        extends AbstractEntityMapper<StonePhotoDraftEntity, StonePhotoEntity> {
    private final Clock clock;
    public StonePhotoDraftEntityToStonePhotoEntityMapper(Clock clock) { this.clock = clock; }
    @Override protected StonePhotoEntity newEntity() { return new StonePhotoEntity(); }
    @Override protected StonePhotoEntity populateEntity(StonePhotoDraftEntity stonePhotoDraftEntity, StonePhotoEntity stonePhotoEntity) {
        stonePhotoEntity.setStone(stonePhotoDraftEntity.getStone());
        stonePhotoEntity.setDraft(stonePhotoDraftEntity);
        stonePhotoEntity.setObjectKey(stonePhotoDraftEntity.getStone().getStorageUuid() + "/" + stonePhotoDraftEntity.getId());
        stonePhotoEntity.setAddedAt(Instant.now(clock));
        stonePhotoEntity.setPosition(stonePhotoDraftEntity.getStone().getPhotos().size());
        stonePhotoEntity.setCopyReady(false);
        return stonePhotoEntity;
    }
}
