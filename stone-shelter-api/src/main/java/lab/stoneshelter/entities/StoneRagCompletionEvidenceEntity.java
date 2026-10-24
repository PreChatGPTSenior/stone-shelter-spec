package lab.stoneshelter.entities;

import java.time.Instant;

/** Current admitted selection proof; never serialized into a completed turn or DTO. */
public record StoneRagCompletionEvidenceEntity(Instant deadline, long controlEpoch, StoneSemanticPreparedTurnEntity selection, RuntimeException failure) {}
