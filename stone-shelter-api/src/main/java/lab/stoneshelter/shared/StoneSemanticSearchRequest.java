package lab.stoneshelter.shared;

import java.time.Instant;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;

/** Internal application request containing already prepared domain criteria. */
public record StoneSemanticSearchRequest(StoneSemanticSearchCriteria criteria, Instant deadline) {}
