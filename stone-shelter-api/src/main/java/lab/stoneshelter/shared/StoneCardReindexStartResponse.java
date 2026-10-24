package lab.stoneshelter.shared;

import java.util.UUID;
import lab.stoneshelter.enums.StoneCardReindexStatus;

public record StoneCardReindexStartResponse(UUID jobId, StoneCardReindexStatus status) {}
