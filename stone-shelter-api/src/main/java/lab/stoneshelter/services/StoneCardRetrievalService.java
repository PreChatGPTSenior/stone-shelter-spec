package lab.stoneshelter.services;

import java.time.Clock;
import lab.stoneshelter.criteria.StoneCardSearchCriteria;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.exceptions.InvalidStoneCardRequestException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneCardSearchResultEntityToStoneCardSearchResponseMapper;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.shared.StoneCardSearchRequest;
import lab.stoneshelter.shared.StoneCardSearchResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneCardRetrievalService {
    private final StoneDocumentEmbeddingService stoneDocumentEmbeddingService;
    private final StoneCardEmbeddingRepository stoneCardEmbeddingRepository;
    private final StoneCardSearchResultEntityToStoneCardSearchResponseMapper stoneCardSearchResultEntityToStoneCardSearchResponseMapper;
    private final StoneDocumentIndexSettings stoneDocumentIndexSettings;
    private final StoneCardSearchSettings stoneCardSearchSettings;
    private final Clock clock;

    public StoneCardRetrievalService(StoneDocumentEmbeddingService stoneDocumentEmbeddingService,
            StoneCardEmbeddingRepository stoneCardEmbeddingRepository,
            StoneCardSearchResultEntityToStoneCardSearchResponseMapper stoneCardSearchResultEntityToStoneCardSearchResponseMapper,
            StoneDocumentIndexSettings stoneDocumentIndexSettings, StoneCardSearchSettings stoneCardSearchSettings, Clock clock) {
        this.stoneDocumentEmbeddingService = stoneDocumentEmbeddingService;
        this.stoneCardEmbeddingRepository = stoneCardEmbeddingRepository;
        this.stoneCardSearchResultEntityToStoneCardSearchResponseMapper = stoneCardSearchResultEntityToStoneCardSearchResponseMapper;
        this.stoneDocumentIndexSettings = stoneDocumentIndexSettings;
        this.stoneCardSearchSettings = stoneCardSearchSettings;
        this.clock = clock;
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public StoneCardSearchResponse search(StoneCardSearchRequest stoneCardSearchRequest) {
        if (stoneCardSearchRequest == null || stoneCardSearchRequest.query() == null
                || stoneCardSearchRequest.query().isBlank() || stoneCardSearchRequest.limit() <= 0) {
            throw new InvalidStoneCardRequestException("A nonempty query and a positive result limit are required.");
        }
        double minimumSimilarity = stoneCardSearchSettings.minimumSimilarity();
        StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity = stoneDocumentEmbeddingService.embed(
                new StoneDocumentEmbeddingCriteria(stoneCardSearchRequest.query(), true,
                        clock.instant().plus(stoneDocumentIndexSettings.embeddingTimeout())));
        try {
            return stoneCardSearchResultEntityToStoneCardSearchResponseMapper.toDto(stoneCardEmbeddingRepository.search(
                    new StoneCardSearchCriteria(stoneDocumentEmbeddingEntity.vector(), stoneDocumentEmbeddingEntity.profile(),
                            stoneCardSearchRequest.limit(), minimumSimilarity)));
        } catch (DataAccessException exception) {
            throw new StoneDocumentDependencyUnavailableException("The stone-card index is unavailable.", exception);
        }
    }
}
