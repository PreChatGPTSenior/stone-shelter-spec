package lab.stoneshelter.entities;

import java.time.Instant;
import java.util.UUID;
import lab.stoneshelter.enums.OutboxDeliveryState;
import tools.jackson.databind.JsonNode;

public class OutboxEventEntity {
    private UUID eventId;
    private String eventType;
    private Integer schemaVersion;
    private Instant occurredAt;
    private JsonNode payload;
    private Instant createdAt;
    private OutboxDeliveryState deliveryState;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String lastError;
    private Instant publishedAt;
    private Instant deadLetteredAt;

    public OutboxEventEntity() {
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public OutboxDeliveryState getDeliveryState() { return deliveryState; }
    public void setDeliveryState(OutboxDeliveryState deliveryState) { this.deliveryState = deliveryState; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }
    public void setDeadLetteredAt(Instant deadLetteredAt) { this.deadLetteredAt = deadLetteredAt; }
}
