package lab.stoneshelter.services;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.exceptions.InvalidStoneCardReindexRequestException;
import lab.stoneshelter.exceptions.StoneCardReindexNotFoundException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper;
import lab.stoneshelter.repositories.StoneCardReindexJobRepository;
import lab.stoneshelter.shared.StoneCardReindexStartRequest;
import lab.stoneshelter.shared.StoneCardReindexStartResponse;
import lab.stoneshelter.shared.StoneCardReindexStatusResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneCardReindexService {
    private final TransactionTemplate statusReadTransaction;
    private final StoneCardReindexTransactionService stoneCardReindexTransactionService;
    private final StoneCardReindexJobRepository stoneCardReindexJobRepository;
    private final StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper startResponseMapper;
    private final StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper statusResponseMapper;

    public StoneCardReindexService(StoneCardReindexTransactionService stoneCardReindexTransactionService,
            StoneCardReindexJobRepository stoneCardReindexJobRepository,
            StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper startResponseMapper,
            StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper statusResponseMapper, PlatformTransactionManager transactionManager) {
        this.stoneCardReindexTransactionService = stoneCardReindexTransactionService;
        this.stoneCardReindexJobRepository = stoneCardReindexJobRepository;
        this.startResponseMapper = startResponseMapper;
        this.statusResponseMapper = statusResponseMapper;
        statusReadTransaction = new TransactionTemplate(transactionManager);
        statusReadTransaction.setReadOnly(true);
        statusReadTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public StoneCardReindexStartResponse start(StoneCardReindexStartRequest stoneCardReindexStartRequest) {
        if (stoneCardReindexStartRequest.target() == StoneCardReindexTarget.SELECTED
                && (stoneCardReindexStartRequest.stoneIds() == null || stoneCardReindexStartRequest.stoneIds().isEmpty())) {
            throw new InvalidStoneCardReindexRequestException("SELECTED requires at least one stone ID.");
        }
        if (stoneCardReindexStartRequest.target() == StoneCardReindexTarget.ALL
                && stoneCardReindexStartRequest.stoneIds() != null && !stoneCardReindexStartRequest.stoneIds().isEmpty()) {
            throw new InvalidStoneCardReindexRequestException("ALL does not accept selected stone IDs.");
        }
        try {
            return startResponseMapper.toDto(stoneCardReindexTransactionService.start(stoneCardReindexStartRequest.target(),
                    stoneCardReindexStartRequest.stoneIds() == null ? List.of() : stoneCardReindexStartRequest.stoneIds()));
        } catch (DataAccessException | CannotCreateTransactionException exception) {
            throw new StoneDocumentDependencyUnavailableException("Reindex job storage is unavailable.", exception);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public StoneCardReindexStatusResponse findById(UUID jobId) {
        try {
            return statusReadTransaction.execute(status -> statusResponseMapper.toDto(stoneCardReindexJobRepository.findById(jobId)
                    .orElseThrow(() -> new StoneCardReindexNotFoundException(jobId))));
        } catch (DataAccessException | CannotCreateTransactionException exception) {
            throw new StoneDocumentDependencyUnavailableException("Reindex job storage is unavailable.", exception);
        }
    }
}
