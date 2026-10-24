package lab.stoneshelter.entities;

import java.util.UUID;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneRagCompletionKind;

public record StoneRagTurnStateEntity(StoneRagMode mode, StoneRagCompletionKind completion,
        UUID selectionReferenceTurnId, boolean actualSelection) {}
