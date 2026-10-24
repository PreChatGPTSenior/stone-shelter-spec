package lab.stoneshelter.shared;

import java.util.UUID;
import java.util.List;
import java.time.Instant;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.enums.StoneCardReindexStatus;

public class StoneCardReindexStatusResponse {
    private UUID jobId;
    private StoneCardReindexTarget target;
    private StoneCardReindexStatus status;
    private long totalCount;
    private long processedCount;
    private long enqueuedCount;
    private long skippedCount;
    private long failedCount;
    private long reconciledCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<StoneCardReindexError> errors;

    public StoneCardReindexStatusResponse() {}
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public StoneCardReindexTarget getTarget() { return target; }
    public void setTarget(StoneCardReindexTarget target) { this.target = target; }
    public StoneCardReindexStatus getStatus() { return status; }
    public void setStatus(StoneCardReindexStatus status) { this.status = status; }
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getProcessedCount() { return processedCount; }
    public void setProcessedCount(long processedCount) { this.processedCount = processedCount; }
    public long getEnqueuedCount() { return enqueuedCount; }
    public void setEnqueuedCount(long enqueuedCount) { this.enqueuedCount = enqueuedCount; }
    public long getSkippedCount() { return skippedCount; }
    public void setSkippedCount(long skippedCount) { this.skippedCount = skippedCount; }
    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }
    public long getReconciledCount() { return reconciledCount; }
    public void setReconciledCount(long reconciledCount) { this.reconciledCount = reconciledCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<StoneCardReindexError> getErrors() { return errors; }
    public void setErrors(List<StoneCardReindexError> errors) { this.errors = errors; }
}
