package lab.stoneshelter.services;

import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.shared.OutboxWriteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class OutboxPayloadValidationService {
    public static final int MAX_PAYLOAD_BYTES = 262144;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OutboxWriteRequest validate(OutboxWriteRequest outboxWriteRequest) {
        if (outboxWriteRequest == null || outboxWriteRequest.eventId() == null
                || outboxWriteRequest.eventType() == null || outboxWriteRequest.eventType().isBlank()
                || outboxWriteRequest.schemaVersion() == null || outboxWriteRequest.schemaVersion() <= 0
                || outboxWriteRequest.payload() == null || !outboxWriteRequest.payload().isObject()
                || outboxWriteRequest.payload().size() == 0) {
            throw new OutboxValidationException("An event ID, nonblank type, positive schema version and nonempty JSON-object payload are required.");
        }
        try {
            if (jsonMapper.writeValueAsBytes(outboxWriteRequest.payload()).length > MAX_PAYLOAD_BYTES) {
                throw new OutboxValidationException("The compact UTF-8 event payload exceeds 256 KiB.");
            }
        } catch (JacksonException exception) {
            throw new OutboxValidationException("The event payload cannot be serialized as JSON.", exception);
        }
        return outboxWriteRequest;
    }
}
