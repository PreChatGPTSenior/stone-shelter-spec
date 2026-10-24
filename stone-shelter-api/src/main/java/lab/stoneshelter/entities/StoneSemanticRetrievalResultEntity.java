package lab.stoneshelter.entities;

import java.util.List;

public record StoneSemanticRetrievalResultEntity(List<StoneSemanticCandidateEntity> candidates, List<Passage> passages) {
    public record Passage(StoneDocumentFragmentEntity fragment, double score) {}
    public StoneSemanticRetrievalResultEntity { candidates = List.copyOf(candidates); passages = List.copyOf(passages); }
}
