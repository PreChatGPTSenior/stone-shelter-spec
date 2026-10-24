package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.repositories.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class OutboxDeliveryTransactionService {
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;
    private final OutboxDeliverySettings outboxDeliverySettings;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OutboxDeliveryTransactionService(OutboxEventRepository outboxEventRepository, Clock clock,
            OutboxDeliverySettings outboxDeliverySettings) {
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
        this.outboxDeliverySettings = outboxDeliverySettings;
    }

    @Transactional(readOnly = true)
    public List<OutboxEventEntity> findEligible() {
        return outboxEventRepository.findEligible(clock.instant(), outboxDeliverySettings.batchSize());
    }

    public OutboxEventEntity markPublished(UUID eventId) {
        return outboxEventRepository.markPublished(eventId, clock.instant());
    }

    public OutboxEventEntity recordFailure(OutboxEventEntity outboxEventEntity, String reason, String originalTopic) {
        Instant now = clock.instant();
        return outboxEventRepository.recordFailure(outboxEventEntity,
                jsonMapper.writeValueAsString(jsonMapper.createObjectNode().put("message", reason).put("originalTopic", originalTopic)),
                outboxEventEntity.getAttemptCount() >= 2 ? now : now.plusSeconds(outboxEventEntity.getAttemptCount() == 0 ? 2 : 4));
    }

    public OutboxEventEntity markDeadLettered(UUID eventId) {
        return outboxEventRepository.markDeadLettered(eventId, clock.instant());
    }

    public OutboxEventEntity deferDeadLetter(UUID eventId) {
        return outboxEventRepository.deferDeadLetter(eventId, clock.instant().plus(outboxDeliverySettings.pollInterval()));
    }
}
