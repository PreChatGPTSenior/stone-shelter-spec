package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import lab.stoneshelter.exceptions.OutboxIdentityConflictException;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.mappers.entities.OutboxWriteRequestToOutboxEventEntityMapper;
import lab.stoneshelter.repositories.OutboxEventRepository;
import lab.stoneshelter.shared.OutboxWriteRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OutboxService {
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadValidationService outboxPayloadValidationService;
    private final OutboxWriteRequestToOutboxEventEntityMapper outboxWriteRequestToOutboxEventEntityMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository outboxEventRepository,
            OutboxPayloadValidationService outboxPayloadValidationService,
            OutboxWriteRequestToOutboxEventEntityMapper outboxWriteRequestToOutboxEventEntityMapper, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxPayloadValidationService = outboxPayloadValidationService;
        this.outboxWriteRequestToOutboxEventEntityMapper = outboxWriteRequestToOutboxEventEntityMapper;
        this.clock = clock;
    }

    public OutboxEventEntity save(OutboxWriteRequest outboxWriteRequest) {
        OutboxEventEntity outboxEventEntity = outboxWriteRequestToOutboxEventEntityMapper.toEntity(
                outboxPayloadValidationService.validate(outboxWriteRequest));
        Instant createdAt = clock.instant();
        outboxEventEntity.setOccurredAt(createdAt);
        outboxEventEntity.setCreatedAt(createdAt);
        outboxEventEntity.setDeliveryState(OutboxDeliveryState.PENDING);
        OutboxEventEntity persistedOutboxEventEntity;
        try {
            persistedOutboxEventEntity = outboxEventRepository.save(outboxEventEntity);
        } catch (DataAccessException exception) {
            throw new OutboxWriteException("The outgoing event could not be persisted.", exception);
        }
        if (!persistedOutboxEventEntity.getEventType().equals(outboxEventEntity.getEventType())
                || !persistedOutboxEventEntity.getSchemaVersion().equals(outboxEventEntity.getSchemaVersion())) {
            throw new OutboxIdentityConflictException(outboxEventEntity.getEventId());
        }
        return persistedOutboxEventEntity;
    }
}
