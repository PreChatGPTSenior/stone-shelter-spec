package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneType;

public record StoneSemanticEvidenceCandidateRequest(long id, StoneType stoneType, String card,
        List<StoneSemanticEvidenceSourceRequest> sources) {}
