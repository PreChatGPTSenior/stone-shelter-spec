package lab.stoneshelter.services;

import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.exceptions.OutboxValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional
public class OutboxEventRoutingService {
    private final OutboxDeliverySettings outboxDeliverySettings;

    public OutboxEventRoutingService(OutboxDeliverySettings outboxDeliverySettings) {
        this.outboxDeliverySettings = outboxDeliverySettings;
    }

    @Transactional(readOnly = true)
    public OutboxDeliverySettings.Route route(OutboxEventEntity outboxEventEntity) {
        return outboxDeliverySettings.route(outboxEventEntity.getEventType());
    }

    @Transactional(readOnly = true)
    public String key(OutboxEventEntity outboxEventEntity, OutboxDeliverySettings.Route route) {
        if (route.keyField() == null) return outboxEventEntity.getEventId().toString();
        JsonNode key = outboxEventEntity.getPayload().get(route.keyField());
        if (key == null || key.isNull() || !(key.isTextual() || key.isNumber() || key.isBoolean()) || key.asText().isBlank()) {
            throw new OutboxValidationException("The configured Kafka key field is missing or is not a nonblank scalar.");
        }
        return key.asText();
    }
}
