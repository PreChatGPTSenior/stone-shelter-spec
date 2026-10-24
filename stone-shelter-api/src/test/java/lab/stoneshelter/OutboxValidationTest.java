package lab.stoneshelter;

import java.util.UUID;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.mappers.entities.OutboxWriteRequestToOutboxEventEntityMapper;
import lab.stoneshelter.services.OutboxPayloadValidationService;
import lab.stoneshelter.shared.OutboxWriteRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxValidationTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final OutboxPayloadValidationService outboxPayloadValidationService = new OutboxPayloadValidationService();
    private final UUID eventId = UUID.randomUUID();

    @Test
    void acceptsUnknownTypesAndPreservesMappedInputWithoutSharingMutablePayload() {
        ObjectNode payload = jsonMapper.createObjectNode().put("stoneId", 19);
        OutboxWriteRequest outboxWriteRequest = new OutboxWriteRequest(eventId, "custom.event", 37, payload);
        assertThat(outboxPayloadValidationService.validate(outboxWriteRequest)).isSameAs(outboxWriteRequest);
        OutboxWriteRequestToOutboxEventEntityMapper mapper = new OutboxWriteRequestToOutboxEventEntityMapper();
        OutboxEventEntity outboxEventEntity = mapper.toEntity(outboxWriteRequest);
        payload.put("stoneId", 20);
        assertThat(outboxEventEntity.getEventId()).isEqualTo(eventId);
        assertThat(outboxEventEntity.getEventType()).isEqualTo("custom.event");
        assertThat(outboxEventEntity.getSchemaVersion()).isEqualTo(37);
        assertThat(outboxEventEntity.getPayload().path("stoneId").asInt()).isEqualTo(19);
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void rejectsMissingIdentityBlankTypesAndInvalidVersions() {
        JsonNode payload = jsonMapper.createObjectNode().put("stoneId", 19);
        for (OutboxWriteRequest invalid : new OutboxWriteRequest[] {
                null, new OutboxWriteRequest(null, "event", 1, payload),
                new OutboxWriteRequest(eventId, null, 1, payload),
                new OutboxWriteRequest(eventId, "", 1, payload),
                new OutboxWriteRequest(eventId, " \t\n", 1, payload),
                new OutboxWriteRequest(eventId, "event", null, payload),
                new OutboxWriteRequest(eventId, "event", 0, payload),
                new OutboxWriteRequest(eventId, "event", -1, payload) }) {
            assertThatThrownBy(() -> outboxPayloadValidationService.validate(invalid))
                    .isInstanceOf(OutboxValidationException.class);
        }
    }

    @Test
    void rejectsNullEmptyArraysAndScalarPayloadsButAcceptsNonemptyObjects() {
        for (JsonNode invalid : new JsonNode[] { null, jsonMapper.readTree("null"),
                jsonMapper.readTree("{}"), jsonMapper.readTree("[]"), jsonMapper.readTree("[1]"),
                jsonMapper.readTree("42"), jsonMapper.readTree("true"), jsonMapper.readTree("\"value\"") }) {
            assertThatThrownBy(() -> outboxPayloadValidationService.validate(request(invalid)))
                    .isInstanceOf(OutboxValidationException.class);
        }
        assertThat(outboxPayloadValidationService.validate(request(jsonMapper.readTree("{\"optional\":null}")))).isNotNull();
    }

    @Test
    void acceptsExactByteLimitAndRejectsOneMoreByte() {
        int overhead = jsonMapper.writeValueAsBytes(jsonMapper.createObjectNode().put("value", "")).length;
        ObjectNode payload = jsonMapper.createObjectNode().put("value", "a".repeat(262144 - overhead));
        assertThat(jsonMapper.writeValueAsBytes(payload)).hasSize(262144);
        assertThat(outboxPayloadValidationService.validate(request(payload))).isNotNull();
        payload.put("value", "a".repeat(262145 - overhead));
        assertThatThrownBy(() -> outboxPayloadValidationService.validate(request(payload)))
                .isInstanceOf(OutboxValidationException.class);
    }

    @Test
    void countsUtf8BytesAndIgnoresFormattingWhitespaceOutsideStringValues() {
        int overhead = jsonMapper.writeValueAsBytes(jsonMapper.createObjectNode().put("value", "")).length;
        ObjectNode payload = jsonMapper.createObjectNode().put("value", "é".repeat((262144 - overhead) / 2));
        assertThat(jsonMapper.writeValueAsBytes(payload)).hasSize(262144);
        assertThat(outboxPayloadValidationService.validate(request(payload))).isNotNull();
        payload.put("value", payload.path("value").asText() + "é");
        assertThatThrownBy(() -> outboxPayloadValidationService.validate(request(payload)))
                .isInstanceOf(OutboxValidationException.class);
        assertThat(outboxPayloadValidationService.validate(request(
                jsonMapper.readTree("{\n" + " ".repeat(262144) + "\"value\":\"  kept  \"\n}")))
                .payload().path("value").asText()).isEqualTo("  kept  ");
    }

    private OutboxWriteRequest request(JsonNode payload) {
        return new OutboxWriteRequest(eventId, "event", 1, payload);
    }
}
