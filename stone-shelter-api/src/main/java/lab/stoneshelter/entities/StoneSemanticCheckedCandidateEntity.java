package lab.stoneshelter.entities;

import java.util.List;
import lab.stoneshelter.criteria.StoneSemanticWish;

/** Persisted proof only: no vectors, transport state or mutable JPA association. */
public record StoneSemanticCheckedCandidateEntity(long id, List<StoneSemanticWish> matched,
        List<StoneSemanticWish> unmatched, List<StoneSemanticEvidenceResultEntity.Judgement> evidence) {
    public StoneSemanticCheckedCandidateEntity { matched = List.copyOf(matched); unmatched = List.copyOf(unmatched); evidence = List.copyOf(evidence); }
}
