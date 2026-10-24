package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.criteria.StoneSemanticPassageCriteria;
import lab.stoneshelter.criteria.StoneRagKnowledgeCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneRagKnowledgeResultEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper;
import lab.stoneshelter.repositories.StoneRagKnowledgeRepository;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.shared.StoneRagKnowledgeRequest;
import lab.stoneshelter.shared.StoneRagKnowledgeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneRagKnowledgeService {
    private record Retrieval(StoneRagKnowledgeRepository cards, StoneSemanticSearchRepository passages) {}
    private record Components(Retrieval retrieval, StoneDocumentEmbeddingService embedding,
            StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper response, StoneSemanticSearchSettings settings) {}
    private final Components components;
    private final Clock clock;
    public StoneRagKnowledgeService(StoneRagKnowledgeRepository cards, StoneSemanticSearchRepository passages, StoneDocumentEmbeddingService embedding,
            StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper mapper, StoneSemanticSearchSettings settings, Clock clock) {
        components = new Components(new Retrieval(cards, passages), embedding, mapper, settings); this.clock = clock;
    }
    public StoneRagKnowledgeResponse search(StoneRagKnowledgeRequest request) {
        StoneSemanticSearchExecutionEntity execution = new StoneSemanticSearchExecutionEntity(); execution.setDeadline(request.deadline()); execution.setModelDeadline(request.deadline());
        return components.response().toDto(prepare(request.criteria(), execution));
    }
    public StoneRagKnowledgeResultEntity prepare(StoneRagKnowledgeCriteria criteria, StoneSemanticSearchExecutionEntity execution) {
        if (criteria.question() == null || criteria.question().isBlank()) throw new IllegalArgumentException("A knowledge question is required");
        if (execution.getModelDeadline() == null || !clock.instant().isBefore(execution.getModelDeadline())) throw new StoneChatUnavailableException();
        StoneRagKnowledgeResultEntity result = new StoneRagKnowledgeResultEntity();
        if (criteria.stoneId() != null) {
            result.setStone(components.retrieval().cards().findById(criteria.stoneId()).orElse(null));
            if (result.getStone() == null) return result;
        }
        String query = criteria.question().strip();
        StoneDocumentEmbeddingEntity vector = execution.getQueryCache().get(query);
        if (vector == null) {
            if (execution.getQueryEmbeddings() >= 2) throw new StoneRagAnswerLimitExceededException();
            Instant deadline = clock.instant().plusSeconds(50); if (execution.getModelDeadline().isBefore(deadline)) deadline = execution.getModelDeadline();
            execution.setQueryEmbeddings(execution.getQueryEmbeddings() + 1);
            vector = components.embedding().embed(new StoneDocumentEmbeddingCriteria(query, true, deadline, ModelAdmissionMode.CUSTOMER));
            execution.getQueryCache().put(query, vector);
        }
        result.setPassages(components.retrieval().passages().findPassages(new StoneSemanticPassageCriteria(vector,
                result.getStone() == null ? criteria.stoneTypes() : List.of(result.getStone().getStoneType()),
                new StoneSemanticPassageCriteria.Bounds(components.settings().documentLimit(), components.settings().documentMinimumSimilarity()))));
        if (!clock.instant().isBefore(execution.getModelDeadline())) throw new StoneChatUnavailableException();
        return result;
    }
}
