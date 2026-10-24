package lab.stoneshelter.entities;

import java.time.Instant;
import java.util.UUID;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;

public class StoneDocumentIndexOperationEntity {
    private UUID idempotencyKey;
    private String documentKey;
    private StoneDocumentIndexOperationKind kind;
    private StoneDocumentIndexOperationState state;
    private UUID ownerToken;
    private Instant deadline;
    private Instant startedAt;
    private Instant committedAt;
    private Instant replayUntil;
    private String sourceVersion;
    private Integer indexedFragments;
    private StoneDocumentIndexOutcome outcome;

    public StoneDocumentIndexOperationEntity() {
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getDocumentKey() { return documentKey; }
    public void setDocumentKey(String documentKey) { this.documentKey = documentKey; }
    public StoneDocumentIndexOperationKind getKind() { return kind; }
    public void setKind(StoneDocumentIndexOperationKind kind) { this.kind = kind; }
    public StoneDocumentIndexOperationState getState() { return state; }
    public void setState(StoneDocumentIndexOperationState state) { this.state = state; }
    public UUID getOwnerToken() { return ownerToken; }
    public void setOwnerToken(UUID ownerToken) { this.ownerToken = ownerToken; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCommittedAt() { return committedAt; }
    public void setCommittedAt(Instant committedAt) { this.committedAt = committedAt; }
    public Instant getReplayUntil() { return replayUntil; }
    public void setReplayUntil(Instant replayUntil) { this.replayUntil = replayUntil; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public Integer getIndexedFragments() { return indexedFragments; }
    public void setIndexedFragments(Integer indexedFragments) { this.indexedFragments = indexedFragments; }
    public StoneDocumentIndexOutcome getOutcome() { return outcome; }
    public void setOutcome(StoneDocumentIndexOutcome outcome) { this.outcome = outcome; }
}
