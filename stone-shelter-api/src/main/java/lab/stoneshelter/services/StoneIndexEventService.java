package lab.stoneshelter.services;

import java.util.Objects;
import java.util.UUID;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneIndexSourceVersionEntity;
import lab.stoneshelter.enums.StoneIndexEventType;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneIndexUpdatePayloadMapper;
import lab.stoneshelter.repositories.StoneIndexSourceVersionRepository;
import lab.stoneshelter.shared.OutboxWriteRequest;
import lab.stoneshelter.shared.StoneIndexDeletePayload;
import lab.stoneshelter.shared.StoneIndexUpdatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StoneIndexEventService {
    private static final Logger LOG = LoggerFactory.getLogger(StoneIndexEventService.class);
    private final StoneIndexSourceVersionRepository stoneIndexSourceVersionRepository;
    private final StoneEntityToStoneIndexUpdatePayloadMapper stoneEntityToStoneIndexUpdatePayloadMapper;
    private final OutboxService outboxService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StoneIndexEventService(StoneIndexSourceVersionRepository stoneIndexSourceVersionRepository,
            StoneEntityToStoneIndexUpdatePayloadMapper stoneEntityToStoneIndexUpdatePayloadMapper, OutboxService outboxService) {
        this.stoneIndexSourceVersionRepository = stoneIndexSourceVersionRepository;
        this.stoneEntityToStoneIndexUpdatePayloadMapper = stoneEntityToStoneIndexUpdatePayloadMapper;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public StoneIndexUpdatePayload snapshot(StoneEntity stoneEntity) {
        return stoneEntityToStoneIndexUpdatePayloadMapper.toDto(stoneEntity);
    }

    public StoneEntity publishUpdateIfChanged(StoneEntity stoneEntity, StoneIndexUpdatePayload previousSnapshot) {
        return Objects.equals(stoneEntity.getName(), previousSnapshot.getName())
                && stoneEntity.getStoneType() == previousSnapshot.getStoneType()
                && stoneEntity.getStoneSize() == previousSnapshot.getStoneSize()
                && Objects.equals(stoneEntity.getBiography(), previousSnapshot.getBiography())
                ? stoneEntity : publishUpdate(stoneEntity);
    }

    public StoneEntity publishUpdate(StoneEntity stoneEntity) {
        long sourceVersion = stoneIndexSourceVersionRepository.increment(new StoneIndexSourceVersionEntity(stoneEntity.getId(), 0)).sourceVersion();
        LOG.debug("Persisted index update event {}", outboxService.save(new OutboxWriteRequest(UUID.randomUUID(),
                StoneIndexEventType.STONE_CARD_INDEX_UPDATED.name(), StoneIndexEventType.SCHEMA_VERSION,
                jsonMapper.valueToTree(stoneEntityToStoneIndexUpdatePayloadMapper.toDto(stoneEntity, sourceVersion)))).getEventId());
        return stoneEntity;
    }

    public StoneEntity publishDelete(StoneEntity stoneEntity) {
        long sourceVersion = stoneIndexSourceVersionRepository.increment(new StoneIndexSourceVersionEntity(stoneEntity.getId(), 0)).sourceVersion();
        LOG.debug("Persisted index delete event {}", outboxService.save(new OutboxWriteRequest(UUID.randomUUID(),
                StoneIndexEventType.STONE_CARD_INDEX_DELETED.name(), StoneIndexEventType.SCHEMA_VERSION,
                jsonMapper.valueToTree(new StoneIndexDeletePayload(stoneEntity.getId(), sourceVersion)))).getEventId());
        return stoneEntity;
    }
}
