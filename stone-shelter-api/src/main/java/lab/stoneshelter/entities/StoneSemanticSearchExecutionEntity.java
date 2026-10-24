package lab.stoneshelter.entities;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;

/** Private per-turn state; never a persisted/public conversation response. */
public class StoneSemanticSearchExecutionEntity {
    private StoneExactSearchResultEntity exactResult;
    public StoneExactSearchResultEntity getExactResult() { return exactResult; }
    public void setExactResult(StoneExactSearchResultEntity value) { exactResult = value; }
    private Instant deadline;
    private Instant modelDeadline;
    private int evidenceCalls;
    private int interpretationCalls;
    private int queryEmbeddings;
    private int generationCalls;
    private boolean refreshSpent;
    private Map<String, StoneDocumentEmbeddingEntity> queryCache = new HashMap<>();
    private Map<String, StoneSemanticEvidenceResultEntity.Judgement> evidenceCache = new HashMap<>();
    public StoneSemanticSearchExecutionEntity() {}
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public Instant getModelDeadline() { return modelDeadline; }
    public void setModelDeadline(Instant modelDeadline) { this.modelDeadline = modelDeadline; }
    public int getEvidenceCalls() { return evidenceCalls; }
    public void setEvidenceCalls(int evidenceCalls) { this.evidenceCalls = evidenceCalls; }
    public int getInterpretationCalls() { return interpretationCalls; }
    public void setInterpretationCalls(int interpretationCalls) { this.interpretationCalls = interpretationCalls; }
    public int getQueryEmbeddings() { return queryEmbeddings; }
    public void setQueryEmbeddings(int queryEmbeddings) { this.queryEmbeddings = queryEmbeddings; }
    public int getGenerationCalls() { return generationCalls; }
    public void setGenerationCalls(int generationCalls) { this.generationCalls = generationCalls; }
    public boolean getRefreshSpent() { return refreshSpent; }
    public void setRefreshSpent(boolean refreshSpent) { this.refreshSpent = refreshSpent; }
    public Map<String, StoneDocumentEmbeddingEntity> getQueryCache() { return queryCache; }
    public void setQueryCache(Map<String, StoneDocumentEmbeddingEntity> queryCache) { this.queryCache = queryCache; }
    public Map<String, StoneSemanticEvidenceResultEntity.Judgement> getEvidenceCache() { return evidenceCache; }
    public void setEvidenceCache(Map<String, StoneSemanticEvidenceResultEntity.Judgement> evidenceCache) { this.evidenceCache = evidenceCache; }
    public void consumeEvidenceCall(int maximum) {
        if (evidenceCalls >= maximum) throw new StoneSemanticSearchLimitExceededException();
        evidenceCalls++;
    }
}
