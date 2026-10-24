package lab.stoneshelter.criteria;

import java.util.List;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;

public record StoneSemanticRetrievalCriteria(StoneSemanticSearchCriteria selection, StoneDocumentEmbeddingEntity query,
        List<StoneSemanticRetrievalResultEntity.Passage> passages, Bounds bounds) {
    public record Bounds(int limit, double minimumCardSimilarity) {
        public Bounds {
            if (limit < 6 || !Double.isFinite(minimumCardSimilarity) || minimumCardSimilarity < -1 || minimumCardSimilarity > 1) {
                throw new IllegalArgumentException("Invalid candidate retrieval bounds");
            }
        }
    }
    public StoneSemanticRetrievalCriteria { passages = List.copyOf(passages); }
}
