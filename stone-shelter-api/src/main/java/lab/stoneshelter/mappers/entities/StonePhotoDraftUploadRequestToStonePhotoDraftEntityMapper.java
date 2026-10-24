package lab.stoneshelter.mappers.entities;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lab.stoneshelter.entities.StonePhotoDraftEntity;
import lab.stoneshelter.shared.StonePhotoDraftUploadRequest;
import org.springframework.stereotype.Component;

@Component
public final class StonePhotoDraftUploadRequestToStonePhotoDraftEntityMapper
        extends AbstractEntityMapper<StonePhotoDraftUploadRequest, StonePhotoDraftEntity> {
    private final Clock clock;
    public StonePhotoDraftUploadRequestToStonePhotoDraftEntityMapper(Clock clock) { this.clock = clock; }
    @Override protected StonePhotoDraftEntity newEntity() {
        var stonePhotoDraftEntity = new StonePhotoDraftEntity();
        stonePhotoDraftEntity.setId(UUID.randomUUID());
        stonePhotoDraftEntity.setObjectKey("draft/" + stonePhotoDraftEntity.getId());
        return stonePhotoDraftEntity;
    }
    @Override protected StonePhotoDraftEntity populateEntity(StonePhotoDraftUploadRequest request, StonePhotoDraftEntity stonePhotoDraftEntity) {
        stonePhotoDraftEntity.setUploadedAt(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        stonePhotoDraftEntity.setExpiresAt(stonePhotoDraftEntity.getUploadedAt().plus(Duration.ofHours(24)));
        return stonePhotoDraftEntity;
    }
}
