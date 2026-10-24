package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneSemanticEvidenceVerdict;

public record StoneSemanticWishEvidenceResponse(String key, String sourceId, StoneSemanticEvidenceVerdict verdict, String quote) {}
