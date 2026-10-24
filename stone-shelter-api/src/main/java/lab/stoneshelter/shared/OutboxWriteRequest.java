package lab.stoneshelter.shared;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record OutboxWriteRequest(UUID eventId, String eventType, Integer schemaVersion, JsonNode payload) {
}
