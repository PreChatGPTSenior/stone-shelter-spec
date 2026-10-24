package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.enums.StoneType;

public record StoneSemanticEvidenceSourceRequest(String id, StoneSemanticSupportSource scope, StoneType stoneType, String text) {}
