package lab.stoneshelter.shared;

import java.util.List;

public record StoneSemanticEvidenceCandidateResponse(long id, List<StoneSemanticWishEvidenceResponse> wishes) {}
