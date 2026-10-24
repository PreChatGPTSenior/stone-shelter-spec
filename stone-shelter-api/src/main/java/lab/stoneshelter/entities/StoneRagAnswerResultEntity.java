package lab.stoneshelter.entities;

import lab.stoneshelter.enums.StoneRagCompletionKind;

/** Private failure remains outside ready DTOs and completed history. */
public record StoneRagAnswerResultEntity(StoneRagGeneratedAnswerEntity answer, StoneRagCompletionKind completion, RuntimeException failure) {}
