package lab.stoneshelter.entities;

import java.util.List;
import lab.stoneshelter.enums.StoneSemanticEvidenceVerdict;

public record StoneSemanticEvidenceResultEntity(List<Judgement> judgements) {
    public record Judgement(String key, String sourceId, StoneSemanticEvidenceVerdict verdict, String quote) {}
    public StoneSemanticEvidenceResultEntity { judgements = List.copyOf(judgements); }
}
