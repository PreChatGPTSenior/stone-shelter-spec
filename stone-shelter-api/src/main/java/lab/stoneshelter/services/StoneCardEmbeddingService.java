package lab.stoneshelter.services;

import java.time.Clock;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.exceptions.InvalidStoneCardRequestException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import lab.stoneshelter.shared.StoneCardSaveResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneCardEmbeddingService {
    private final StoneDocumentEmbeddingService stoneDocumentEmbeddingService;
    private final StoneCardEmbeddingTransactionService stoneCardEmbeddingTransactionService;
    private final StoneCardSaveRequestToStoneCardEmbeddingEntityMapper stoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
    private final StoneCardEmbeddingEntityToStoneCardSaveResponseMapper stoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
    private final StoneDocumentIndexSettings stoneDocumentIndexSettings;
    private final Clock clock;

    public StoneCardEmbeddingService(StoneDocumentEmbeddingService stoneDocumentEmbeddingService,
            StoneCardEmbeddingTransactionService stoneCardEmbeddingTransactionService,
            StoneCardSaveRequestToStoneCardEmbeddingEntityMapper stoneCardSaveRequestToStoneCardEmbeddingEntityMapper,
            StoneCardEmbeddingEntityToStoneCardSaveResponseMapper stoneCardEmbeddingEntityToStoneCardSaveResponseMapper,
            StoneDocumentIndexSettings stoneDocumentIndexSettings, Clock clock) {
        this.stoneDocumentEmbeddingService = stoneDocumentEmbeddingService;
        this.stoneCardEmbeddingTransactionService = stoneCardEmbeddingTransactionService;
        this.stoneCardSaveRequestToStoneCardEmbeddingEntityMapper = stoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
        this.stoneCardEmbeddingEntityToStoneCardSaveResponseMapper = stoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
        this.stoneDocumentIndexSettings = stoneDocumentIndexSettings;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneCardSaveResponse save(StoneCardSaveRequest stoneCardSaveRequest) {
        try {
            return stoneCardEmbeddingEntityToStoneCardSaveResponseMapper.toDto(
                    stoneCardEmbeddingTransactionService.save(prepare(stoneCardSaveRequest, ModelAdmissionMode.IMPORT)));
        } catch (DataAccessException exception) {
            throw new StoneDocumentDependencyUnavailableException("The stone-card index could not be saved.", exception);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneCardEmbeddingEntity prepare(StoneCardSaveRequest stoneCardSaveRequest) {
        return prepare(stoneCardSaveRequest, ModelAdmissionMode.BACKGROUND);
    }

    private StoneCardEmbeddingEntity prepare(StoneCardSaveRequest stoneCardSaveRequest, ModelAdmissionMode mode) {
        validate(stoneCardSaveRequest);
        StoneCardEmbeddingEntity stoneCardEmbeddingEntity = stoneCardSaveRequestToStoneCardEmbeddingEntityMapper.toEntity(stoneCardSaveRequest);
        StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity = stoneDocumentEmbeddingService.embed(
                new StoneDocumentEmbeddingCriteria(stoneCardEmbeddingEntity.getText(), false,
                        clock.instant().plus(stoneDocumentIndexSettings.embeddingTimeout()), mode));
        stoneCardEmbeddingEntity.setEmbeddingProfile(stoneDocumentEmbeddingEntity.profile());
        stoneCardEmbeddingEntity.setEmbedding(stoneDocumentEmbeddingEntity.vector());
        return stoneCardEmbeddingEntity;
    }

    private void validate(StoneCardSaveRequest stoneCardSaveRequest) {
        if (stoneCardSaveRequest == null || stoneCardSaveRequest.getStoneId() == null || stoneCardSaveRequest.getStoneId() <= 0
                || stoneCardSaveRequest.getName() == null || stoneCardSaveRequest.getName().isBlank()
                || stoneCardSaveRequest.getName().length() > 120 || stoneCardSaveRequest.getStoneType() == null
                || stoneCardSaveRequest.getStoneSize() == null
                || (stoneCardSaveRequest.getBiography() != null && stoneCardSaveRequest.getBiography().length() > 2048)) {
            throw new InvalidStoneCardRequestException("A valid stone-card snapshot is required.");
        }
    }
}
