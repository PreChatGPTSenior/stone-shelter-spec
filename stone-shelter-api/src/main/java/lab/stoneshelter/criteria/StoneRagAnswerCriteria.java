package lab.stoneshelter.criteria;

import java.time.Instant;
import java.util.List;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneSemanticSearchMode;

public class StoneRagAnswerCriteria {
    private StoneRagMode mode;
    private String question;
    private List<StoneSemanticCandidateEntity> candidates = List.of();
    private StoneEntity reference;
    private List<StoneSemanticRetrievalResultEntity.Passage> passages = List.of();
    private StoneSemanticSearchMode selectionMode;
    private Instant deadline;
    public StoneRagAnswerCriteria() {}
    public StoneRagMode getMode() { return mode; }
    public void setMode(StoneRagMode value) { mode = value; }
    public String getQuestion() { return question; }
    public void setQuestion(String value) { question = value; }
    public List<StoneSemanticCandidateEntity> getCandidates() { return candidates; }
    public void setCandidates(List<StoneSemanticCandidateEntity> value) { candidates = value; }
    public StoneEntity getReference() { return reference; }
    public void setReference(StoneEntity value) { reference = value; }
    public List<StoneSemanticRetrievalResultEntity.Passage> getPassages() { return passages; }
    public void setPassages(List<StoneSemanticRetrievalResultEntity.Passage> value) { passages = value; }
    public StoneSemanticSearchMode getSelectionMode() { return selectionMode; }
    public void setSelectionMode(StoneSemanticSearchMode value) { selectionMode = value; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant value) { deadline = value; }
}
