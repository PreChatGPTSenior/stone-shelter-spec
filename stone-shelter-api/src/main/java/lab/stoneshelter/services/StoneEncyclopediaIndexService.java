package lab.stoneshelter.services;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.criteria.StoneDocumentIndexOperationCriteria;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneDocumentIndexOperationEntity;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentIndexConflictException;
import lab.stoneshelter.mappers.dtos.StoneDocumentIndexOperationEntityToStoneEncyclopediaIndexResponseMapper;
import lab.stoneshelter.shared.StoneEncyclopediaIndexResponse;
import lab.stoneshelter.shared.StoneEncyclopediaDeleteResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneEncyclopediaIndexService {
    private final StoneDocumentIndexTransactionService transactions;
    private final StoneDocumentPreparationService preparation;
    private final StoneDocumentIndexSettings settings;
    private final StoneDocumentIndexOperationEntityToStoneEncyclopediaIndexResponseMapper mapper;

    public StoneEncyclopediaIndexService(StoneDocumentIndexTransactionService transactions, StoneDocumentPreparationService preparation,
            StoneDocumentIndexSettings settings, StoneDocumentIndexOperationEntityToStoneEncyclopediaIndexResponseMapper mapper) {
        this.transactions = transactions; this.preparation = preparation; this.settings = settings; this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneEncyclopediaIndexResponse importIndex(UUID key) {
        return mapper.toDto(execute(key, StoneDocumentIndexOperationKind.IMPORT));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneEncyclopediaDeleteResponse delete(UUID key) {
        execute(key, StoneDocumentIndexOperationKind.DELETE);
        return new StoneEncyclopediaDeleteResponse(StoneDocumentIndexEntity.DOCUMENT_KEY);
    }

    private StoneDocumentIndexOperationEntity execute(UUID key, StoneDocumentIndexOperationKind kind) {
        UUID owner = UUID.randomUUID();
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity;
        try {
            stoneDocumentIndexOperationEntity = transactions.claim(new StoneDocumentIndexOperationCriteria(key, kind, owner,
                    transactions.now().plus(settings.importTimeout())));
        } catch (DataAccessException exception) {
            throw new StoneDocumentDependencyUnavailableException("The document operation cannot be admitted.", exception);
        }
        if (stoneDocumentIndexOperationEntity.getState() == StoneDocumentIndexOperationState.SUCCEEDED) return stoneDocumentIndexOperationEntity;
        if (stoneDocumentIndexOperationEntity.getState() == StoneDocumentIndexOperationState.EXPIRED) {
            throw new StoneDocumentIndexConflictException("The key has expired; use a new key.");
        }
        if (stoneDocumentIndexOperationEntity.getState() == StoneDocumentIndexOperationState.FAILED) {
            throw new StoneDocumentIndexConflictException("The attempt failed; use a new key.");
        }
        try {
            return transactions.publish(key, owner, kind == StoneDocumentIndexOperationKind.IMPORT
                    ? preparation.prepare(stoneDocumentIndexOperationEntity.getDeadline()) : List.of());
        } catch (RuntimeException exception) {
            // Synchronous preparation/publication has exited before reconciliation.
            // The transaction lock proves a prior publication transaction has finished.
            StoneDocumentIndexOperationEntity reconciledStoneDocumentIndexOperationEntity;
            try { reconciledStoneDocumentIndexOperationEntity = transactions.reconcileStopped(key, owner); }
            catch (RuntimeException reconciliationFailure) {
                throw new StoneDocumentDependencyUnavailableException("The operation outcome remains uncertain.", reconciliationFailure);
            }
            if (reconciledStoneDocumentIndexOperationEntity.getState() == StoneDocumentIndexOperationState.SUCCEEDED) return reconciledStoneDocumentIndexOperationEntity;
            if (exception instanceof DataAccessException) {
                throw new StoneDocumentDependencyUnavailableException("The document publication failed.", exception);
            }
            throw exception;
        }
    }
}
