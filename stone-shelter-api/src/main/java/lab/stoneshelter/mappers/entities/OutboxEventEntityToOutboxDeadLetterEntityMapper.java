package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.entities.OutboxEventEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OutboxEventEntityToOutboxDeadLetterEntityMapper
        extends AbstractEntityMapper<OutboxEventEntity, OutboxDeadLetterEntity> {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    protected OutboxDeadLetterEntity newEntity() { return new OutboxDeadLetterEntity(); }

    @Override
    protected OutboxDeadLetterEntity populateEntity(OutboxEventEntity outboxEventEntity,
            OutboxDeadLetterEntity outboxDeadLetterEntity) {
        ObjectNode originalEvent = jsonMapper.createObjectNode()
                .put("eventId", outboxEventEntity.getEventId().toString())
                .put("eventType", outboxEventEntity.getEventType())
                .put("schemaVersion", outboxEventEntity.getSchemaVersion())
                .put("occurredAt", outboxEventEntity.getOccurredAt().toString());
        originalEvent.set("payload", outboxEventEntity.getPayload().deepCopy());
        outboxDeadLetterEntity.setOriginalEvent(originalEvent);
        outboxDeadLetterEntity.setFailureStage("PUBLISHER");
        outboxDeadLetterEntity.setAttemptCount(outboxEventEntity.getAttemptCount());
        outboxDeadLetterEntity.setFailureReason(outboxEventEntity.getLastError() == null ? "Publication failed." : outboxEventEntity.getLastError());
        if (outboxEventEntity.getLastError() != null) {
            try {
                JsonNode error = jsonMapper.readTree(outboxEventEntity.getLastError());
                if (error.isObject() && error.path("message").isTextual()) {
                    outboxDeadLetterEntity.setFailureReason(error.path("message").asText());
                    outboxDeadLetterEntity.setOriginalTopic(error.hasNonNull("originalTopic") ? error.path("originalTopic").asText() : null);
                }
            } catch (JacksonException ignored) {
                // Legacy plain-text errors still preserve their complete failure reason.
            }
        }
        return outboxDeadLetterEntity;
    }
}
