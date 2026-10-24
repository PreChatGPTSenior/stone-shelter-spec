package lab.stoneshelter.entities;

import java.time.Instant;

public class StoneCardIndexAttemptEntity {
    private String sourceTopic;
    private int sourcePartition;
    private long sourceOffset;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String lastError;
    private byte[] originalRecord;
    private boolean deadLetterPending;
    private boolean deadLettered;

    public StoneCardIndexAttemptEntity() {}
    public String getSourceTopic() { return sourceTopic; }
    public void setSourceTopic(String sourceTopic) { this.sourceTopic = sourceTopic; }
    public int getSourcePartition() { return sourcePartition; }
    public void setSourcePartition(int sourcePartition) { this.sourcePartition = sourcePartition; }
    public long getSourceOffset() { return sourceOffset; }
    public void setSourceOffset(long sourceOffset) { this.sourceOffset = sourceOffset; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public byte[] getOriginalRecord() { return originalRecord == null ? null : originalRecord.clone(); }
    public void setOriginalRecord(byte[] originalRecord) { this.originalRecord = originalRecord == null ? null : originalRecord.clone(); }
    public boolean getDeadLetterPending() { return deadLetterPending; }
    public void setDeadLetterPending(boolean deadLetterPending) { this.deadLetterPending = deadLetterPending; }
    public boolean getDeadLettered() { return deadLettered; }
    public void setDeadLettered(boolean deadLettered) { this.deadLettered = deadLettered; }
}
