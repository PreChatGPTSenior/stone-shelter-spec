package lab.stoneshelter.services;

import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.shared.StoneSemanticEvidenceRequest;

public interface StoneSemanticEvidenceProvider {
    StoneModelResponseEntity invoke(StoneSemanticEvidenceRequest stoneSemanticEvidenceRequest, String repairReason);
}
