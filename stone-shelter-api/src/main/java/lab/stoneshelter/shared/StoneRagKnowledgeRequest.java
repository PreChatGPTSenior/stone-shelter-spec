package lab.stoneshelter.shared;

import java.time.Instant;
import lab.stoneshelter.criteria.StoneRagKnowledgeCriteria;

public record StoneRagKnowledgeRequest(StoneRagKnowledgeCriteria criteria, Instant deadline) {}
