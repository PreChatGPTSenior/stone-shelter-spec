package lab.stoneshelter.services;

import java.time.Instant;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.criteria.StoneDocumentSearchCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper;
import lab.stoneshelter.repositories.StoneDocumentIndexRepository;
import lab.stoneshelter.shared.StoneDocumentSearchRequest;
import lab.stoneshelter.shared.StoneDocumentSearchResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneDocumentRetrievalService {
    private final StoneDocumentEmbeddingService embeddings;
    private final StoneDocumentIndexRepository repository;
    private final StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper mapper;
    private final StoneDocumentIndexSettings settings;

    public StoneDocumentRetrievalService(StoneDocumentEmbeddingService embeddings, StoneDocumentIndexRepository repository,
            StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper mapper, StoneDocumentIndexSettings settings) {
        this.embeddings = embeddings; this.repository = repository; this.mapper = mapper; this.settings = settings;
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public StoneDocumentSearchResponse search(StoneDocumentSearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank() || request.limit() < 1 || request.limit() > 5) {
            throw new IllegalArgumentException("A nonempty query and a limit from one to five are required.");
        }
        StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity = embeddings.embed(new StoneDocumentEmbeddingCriteria(request.query(), true,
                Instant.now().plus(settings.embeddingTimeout())));
        try { return mapper.toDto(repository.search(new StoneDocumentSearchCriteria(stoneDocumentEmbeddingEntity.vector(), stoneDocumentEmbeddingEntity.profile(), request.limit()))); }
        catch (DataAccessException exception) {
            throw new StoneDocumentDependencyUnavailableException("The passage index is unavailable.", exception);
        }
    }
}
