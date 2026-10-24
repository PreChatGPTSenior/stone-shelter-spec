package lab.stoneshelter.shared;

import java.util.List;

public class StoneRagKnowledgeResponse {
    private StoneRagCandidateRequest reference;
    private List<StoneRagSourceRequest> sources = List.of();
    public StoneRagKnowledgeResponse() {}
    public StoneRagCandidateRequest getReference() { return reference; }
    public void setReference(StoneRagCandidateRequest value) { reference = value; }
    public List<StoneRagSourceRequest> getSources() { return sources; }
    public void setSources(List<StoneRagSourceRequest> value) { sources = value; }
}
