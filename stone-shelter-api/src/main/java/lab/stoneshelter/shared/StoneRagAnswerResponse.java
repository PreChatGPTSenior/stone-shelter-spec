package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneRagCompletionKind;

public record StoneRagAnswerResponse(StoneRagGeneratedAnswerResponse answer, StoneRagCompletionKind completion) {}
