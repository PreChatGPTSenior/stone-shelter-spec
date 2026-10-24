package lab.stoneshelter.entities;

import java.util.UUID;

public class StoneCardReindexItemEntity {
    private UUID jobId;
    private long stoneId;
    private String workKind;
    private String outcome;
    private String error;

    public StoneCardReindexItemEntity() {}
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public long getStoneId() { return stoneId; }
    public void setStoneId(long stoneId) { this.stoneId = stoneId; }
    public String getWorkKind() { return workKind; }
    public void setWorkKind(String workKind) { this.workKind = workKind; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
