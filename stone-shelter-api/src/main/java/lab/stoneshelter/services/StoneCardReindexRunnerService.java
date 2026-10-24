package lab.stoneshelter.services;

import java.sql.SQLException;
import lab.stoneshelter.entities.StoneCardReindexItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneCardReindexRunnerService {
    private static final Logger LOG = LoggerFactory.getLogger(StoneCardReindexRunnerService.class);
    private final StoneCardReindexTransactionService stoneCardReindexTransactionService;
    private final StoneCardReindexSettings stoneCardReindexSettings;

    public StoneCardReindexRunnerService(StoneCardReindexTransactionService stoneCardReindexTransactionService,
            StoneCardReindexSettings stoneCardReindexSettings) {
        this.stoneCardReindexTransactionService = stoneCardReindexTransactionService;
        this.stoneCardReindexSettings = stoneCardReindexSettings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resume() { runBatch(); }

    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public synchronized void runBatch() {
        try {
            var active = stoneCardReindexTransactionService.findActiveJob();
            if (active.isEmpty()) return;
            var job = stoneCardReindexTransactionService.markRunning(active.get().getJobId());
            for (StoneCardReindexItemEntity item : stoneCardReindexTransactionService.findPending(job.getJobId(), stoneCardReindexSettings.batchSize())) {
                try {
                    LOG.debug("Reindex item {} entered {}", item.getStoneId(), stoneCardReindexTransactionService.process(item).getOutcome());
                } catch (RuntimeException exception) {
                    if (storageUnavailable(exception)) throw exception;
                    LOG.warn("Reindex item {} failed independently", stoneCardReindexTransactionService.recordError(item,
                            exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()).getStoneId());
                }
            }
            LOG.debug("Reindex job {} is {}", job.getJobId(), stoneCardReindexTransactionService.finishIfPrepared(job.getJobId()).getStatus());
        } catch (RuntimeException exception) {
            LOG.warn("Reindex work waits for storage/control recovery: {}", exception.getClass().getSimpleName());
        }
    }

    private boolean storageUnavailable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof CannotCreateTransactionException || cause instanceof DataAccessResourceFailureException
                    || cause instanceof TransientDataAccessResourceException || cause instanceof RecoverableDataAccessException
                    || (cause instanceof SQLException sqlException && sqlException.getSQLState() != null && sqlException.getSQLState().startsWith("08"))) return true;
        }
        return false;
    }
}
