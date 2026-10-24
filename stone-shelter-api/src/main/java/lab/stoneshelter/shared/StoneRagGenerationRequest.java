package lab.stoneshelter.shared;

import java.time.Instant;
import java.util.List;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneSemanticSearchMode;

public class StoneRagGenerationRequest {
    private StoneRagMode mode;
    private String question;
    private List<StoneRagCandidateRequest> candidates = List.of();
    private StoneRagCandidateRequest reference;
    private List<StoneRagSourceRequest> sources = List.of();
    private StoneSemanticSearchMode selectionMode;
    private Instant deadline;
    public StoneRagGenerationRequest() {}
    public StoneRagMode getMode() { return mode; }
    public void setMode(StoneRagMode value) { mode = value; }
    public String getQuestion() { return question; }
    public void setQuestion(String value) { question = value; }
    public List<StoneRagCandidateRequest> getCandidates() { return candidates; }
    public void setCandidates(List<StoneRagCandidateRequest> value) { candidates = value; }
    public StoneRagCandidateRequest getReference() { return reference; }
    public void setReference(StoneRagCandidateRequest value) { reference = value; }
    public List<StoneRagSourceRequest> getSources() { return sources; }
    public void setSources(List<StoneRagSourceRequest> value) { sources = value; }
    public StoneSemanticSearchMode getSelectionMode() { return selectionMode; }
    public void setSelectionMode(StoneSemanticSearchMode value) { selectionMode = value; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant value) { deadline = value; }
}
