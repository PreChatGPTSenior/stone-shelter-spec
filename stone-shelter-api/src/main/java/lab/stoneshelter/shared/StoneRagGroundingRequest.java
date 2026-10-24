package lab.stoneshelter.shared;

import java.time.Instant;

public record StoneRagGroundingRequest(StoneRagGenerationRequest facts, String prose, Long stoneId, Instant deadline) {}
