package lab.stoneshelter.shared;

import java.time.Instant;
import java.util.List;

public record StoneSemanticEvidenceRequest(List<StoneSemanticWishRequest> wishes,
        List<StoneSemanticEvidenceSourceRequest> sources, Instant deadline) {
    public StoneSemanticEvidenceRequest { wishes = List.copyOf(wishes); sources = List.copyOf(sources); }
}
