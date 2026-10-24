package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneType;

public record StoneDocumentPassageResponse(String sectionPath, StoneType stoneType, String text, double score) { }
