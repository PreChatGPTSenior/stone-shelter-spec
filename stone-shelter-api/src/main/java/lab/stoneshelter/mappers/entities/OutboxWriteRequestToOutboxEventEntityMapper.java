package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.shared.OutboxWriteRequest;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriteRequestToOutboxEventEntityMapper
        extends AbstractEntityMapper<OutboxWriteRequest, OutboxEventEntity> {
    @Override
    protected OutboxEventEntity newEntity() { return new OutboxEventEntity(); }

    @Override
    protected OutboxEventEntity populateEntity(OutboxWriteRequest outboxWriteRequest,
            OutboxEventEntity outboxEventEntity) {
        outboxEventEntity.setEventId(outboxWriteRequest.eventId());
        outboxEventEntity.setEventType(outboxWriteRequest.eventType());
        outboxEventEntity.setSchemaVersion(outboxWriteRequest.schemaVersion());
        outboxEventEntity.setPayload(outboxWriteRequest.payload().deepCopy());
        return outboxEventEntity;
    }
}
