package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSemanticSupportSource;

public record StoneRagSourceRequest(String id, StoneSemanticSupportSource scope, StoneType stoneType, String text) {}
