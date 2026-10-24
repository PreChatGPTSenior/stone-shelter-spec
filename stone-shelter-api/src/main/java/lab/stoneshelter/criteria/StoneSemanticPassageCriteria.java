package lab.stoneshelter.criteria;

import java.util.List;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.enums.StoneType;

public record StoneSemanticPassageCriteria(StoneDocumentEmbeddingEntity query, List<StoneType> types, Bounds bounds) {
    public record Bounds(int limit, double minimumSimilarity) {
        public Bounds {
            if (limit < 1 || !Double.isFinite(minimumSimilarity) || minimumSimilarity < -1 || minimumSimilarity > 1) {
                throw new IllegalArgumentException("Invalid document retrieval bounds");
            }
        }
    }
    public StoneSemanticPassageCriteria { types = List.copyOf(types); }
}
