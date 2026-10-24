package lab.stoneshelter.mappers.dtos;

import java.time.Clock;
import java.time.Instant;
import lab.stoneshelter.entities.StonePhotoDraftEntity;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.shared.StonePhotoDraftResponse;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoDraftEntityToStonePhotoDraftResponseMapper
        extends AbstractDtoMapper<StonePhotoDraftEntity, StonePhotoDraftResponse> {
    private final MinioPhotoStorageService storage;
    private final Clock clock;
    public StonePhotoDraftEntityToStonePhotoDraftResponseMapper(MinioPhotoStorageService storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }
    @Override protected StonePhotoDraftResponse mapToDto(StonePhotoDraftEntity stonePhotoDraftEntity) {
        return new StonePhotoDraftResponse(stonePhotoDraftEntity.getId(),
                storage.readDraftUrl(stonePhotoDraftEntity.getObjectKey(),
                        (int) Math.min(3600, stonePhotoDraftEntity.getExpiresAt().getEpochSecond() - Instant.now(clock).getEpochSecond()),
                        stonePhotoDraftEntity.getExpiresAt()),
                stonePhotoDraftEntity.getUploadedAt(), stonePhotoDraftEntity.getExpiresAt());
    }
}
