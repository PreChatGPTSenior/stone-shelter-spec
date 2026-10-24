package lab.stoneshelter.entities;

import java.util.Base64;
import tools.jackson.databind.json.JsonMapper;

import tools.jackson.databind.JsonNode;

public class OutboxDeadLetterEntity {
    private JsonNode originalEvent;
    private String failureStage;
    private String failureReason;
    private int attemptCount;
    private String originalTopic;

    public OutboxDeadLetterEntity() {
    }

    public JsonNode getOriginalEvent() { return originalEvent; }
    public void setOriginalEvent(JsonNode originalEvent) { this.originalEvent = originalEvent; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getOriginalTopic() { return originalTopic; }
    public void setOriginalTopic(String originalTopic) { this.originalTopic = originalTopic; }

    public static OutboxDeadLetterEntity fromConsumer(StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity) {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        OutboxDeadLetterEntity outboxDeadLetterEntity = new OutboxDeadLetterEntity();
        try {
            outboxDeadLetterEntity.setOriginalEvent(stoneCardIndexAttemptEntity.getOriginalRecord() == null
                    ? jsonMapper.nullNode() : jsonMapper.readTree(stoneCardIndexAttemptEntity.getOriginalRecord()));
            if (outboxDeadLetterEntity.getOriginalEvent() == null) throw new IllegalArgumentException("Empty record.");
        } catch (RuntimeException exception) {
            outboxDeadLetterEntity.setOriginalEvent(jsonMapper.createObjectNode().put("encoding", "base64")
                    .put("value", Base64.getEncoder().encodeToString(stoneCardIndexAttemptEntity.getOriginalRecord())));
        }
        outboxDeadLetterEntity.setFailureStage("CONSUMER");
        outboxDeadLetterEntity.setFailureReason(stoneCardIndexAttemptEntity.getLastError());
        outboxDeadLetterEntity.setAttemptCount(stoneCardIndexAttemptEntity.getAttemptCount());
        outboxDeadLetterEntity.setOriginalTopic(stoneCardIndexAttemptEntity.getSourceTopic());
        return outboxDeadLetterEntity;
    }
}
