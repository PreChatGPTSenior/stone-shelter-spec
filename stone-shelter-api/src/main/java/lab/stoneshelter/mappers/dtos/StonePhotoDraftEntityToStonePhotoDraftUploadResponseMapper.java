package lab.stoneshelter.mappers.dtos;

import java.time.Clock;
import java.time.Instant;
import lab.stoneshelter.entities.StonePhotoDraftEntity;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.shared.StonePhotoDraftUploadResponse;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoDraftEntityToStonePhotoDraftUploadResponseMapper
        extends AbstractDtoMapper<StonePhotoDraftEntity, StonePhotoDraftUploadResponse> {
    private final MinioPhotoStorageService storage;
    private final Clock clock;
    public StonePhotoDraftEntityToStonePhotoDraftUploadResponseMapper(MinioPhotoStorageService storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }
    @Override protected StonePhotoDraftUploadResponse mapToDto(StonePhotoDraftEntity stonePhotoDraftEntity) {
        return new StonePhotoDraftUploadResponse(stonePhotoDraftEntity.getId(),
                storage.readDraftUrl(stonePhotoDraftEntity.getObjectKey(),
                        (int) Math.min(3600, stonePhotoDraftEntity.getExpiresAt().getEpochSecond() - Instant.now(clock).getEpochSecond()),
                        stonePhotoDraftEntity.getExpiresAt()),
                stonePhotoDraftEntity.getUploadedAt(), stonePhotoDraftEntity.getExpiresAt());
    }
}
