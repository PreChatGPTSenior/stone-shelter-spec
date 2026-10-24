package lab.stoneshelter.services;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StoneCardReindexItemEntity;
import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneIndexSourceVersionEntity;
import lab.stoneshelter.enums.StoneCardReindexStatus;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.enums.StoneIndexEventType;
import lab.stoneshelter.exceptions.StoneCardReindexConflictException;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.repositories.StoneCardIndexStateRepository;
import lab.stoneshelter.repositories.StoneCardReindexControlRepository;
import lab.stoneshelter.repositories.StoneCardReindexItemRepository;
import lab.stoneshelter.repositories.StoneCardReindexJobRepository;
import lab.stoneshelter.repositories.StoneEntityRepository;
import lab.stoneshelter.repositories.StoneIndexSourceVersionRepository;
import lab.stoneshelter.shared.OutboxWriteRequest;
import lab.stoneshelter.shared.StoneIndexDeletePayload;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class StoneCardReindexTransactionService {
    private static final Logger LOG = LoggerFactory.getLogger(StoneCardReindexTransactionService.class);
    private final StoneCardReindexControlRepository controlRepository;
    private final StoneCardReindexJobRepository jobRepository;
    private final StoneCardReindexItemRepository itemRepository;
    private final StoneEntityRepository stoneEntityRepository;
    private final StoneCardEmbeddingRepository stoneCardEmbeddingRepository;
    private final StoneIndexSourceVersionRepository sourceVersionRepository;
    private final StoneCardIndexStateRepository stateRepository;
    private final StoneIndexEventService stoneIndexEventService;
    private final OutboxService outboxService;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StoneCardReindexTransactionService(StoneCardReindexControlRepository controlRepository,
            StoneCardReindexJobRepository jobRepository, StoneCardReindexItemRepository itemRepository,
            StoneEntityRepository stoneEntityRepository, StoneCardEmbeddingRepository stoneCardEmbeddingRepository,
            StoneIndexSourceVersionRepository sourceVersionRepository, StoneCardIndexStateRepository stateRepository,
            StoneIndexEventService stoneIndexEventService, OutboxService outboxService, Clock clock) {
        this.controlRepository = controlRepository;
        this.jobRepository = jobRepository;
        this.itemRepository = itemRepository;
        this.stoneEntityRepository = stoneEntityRepository;
        this.stoneCardEmbeddingRepository = stoneCardEmbeddingRepository;
        this.sourceVersionRepository = sourceVersionRepository;
        this.stateRepository = stateRepository;
        this.stoneIndexEventService = stoneIndexEventService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    public StoneCardReindexJobEntity start(StoneCardReindexTarget target, List<Long> stoneIds) {
        if (controlRepository.findActiveJobIdForUpdate().isPresent()) {
            throw new StoneCardReindexConflictException("A reindex job is already active.");
        }
        List<Long> selectedIds = target == StoneCardReindexTarget.ALL ? stoneEntityRepository.findIds() : stoneIds.stream().distinct().sorted().toList();
        if (target == StoneCardReindexTarget.SELECTED) {
            var existing = new HashSet<>(stoneEntityRepository.findExistingIds(selectedIds));
            for (long stoneId : selectedIds) if (!existing.contains(stoneId)) throw new StoneNotFoundException(stoneId);
        }
        StoneCardReindexJobEntity stoneCardReindexJobEntity = new StoneCardReindexJobEntity();
        stoneCardReindexJobEntity.setJobId(UUID.randomUUID());
        stoneCardReindexJobEntity.setTarget(target);
        stoneCardReindexJobEntity.setStatus(StoneCardReindexStatus.PENDING);
        stoneCardReindexJobEntity.setCreatedAt(clock.instant());
        stoneCardReindexJobEntity.setUpdatedAt(stoneCardReindexJobEntity.getCreatedAt());
        stoneCardReindexJobEntity = controlRepository.activate(jobRepository.save(stoneCardReindexJobEntity));
        for (long stoneId : selectedIds) {
            LOG.debug("Prepared live manifest item {}", savePendingItem(stoneCardReindexJobEntity.getJobId(), stoneId, "CARD").getStoneId());
        }
        if (target == StoneCardReindexTarget.ALL) {
            for (long stoneId : stoneCardEmbeddingRepository.findOrphanIds()) {
                LOG.debug("Prepared orphan manifest item {}", savePendingItem(stoneCardReindexJobEntity.getJobId(), stoneId, "ORPHAN").getStoneId());
            }
        }
        return jobRepository.findById(stoneCardReindexJobEntity.getJobId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<StoneCardReindexJobEntity> findActiveJob() {
        return controlRepository.findActiveJobId().flatMap(jobRepository::findById);
    }

    @Transactional(readOnly = true)
    public List<StoneCardReindexItemEntity> findPending(UUID jobId, int batchSize) {
        return itemRepository.findPending(jobId, batchSize);
    }

    public StoneCardReindexJobEntity markRunning(UUID jobId) {
        StoneCardReindexJobEntity stoneCardReindexJobEntity = jobRepository.findById(jobId).orElseThrow();
        stoneCardReindexJobEntity.setStatus(StoneCardReindexStatus.RUNNING);
        stoneCardReindexJobEntity.setUpdatedAt(clock.instant());
        return jobRepository.save(stoneCardReindexJobEntity);
    }

    public StoneCardReindexItemEntity process(StoneCardReindexItemEntity selectedItem) {
        StoneCardReindexItemEntity stoneCardReindexItemEntity = itemRepository.findByIdForUpdate(selectedItem).orElseThrow();
        if (!"PENDING".equals(stoneCardReindexItemEntity.getOutcome())) return stoneCardReindexItemEntity;
        Optional<StoneEntity> stoneEntity = stoneEntityRepository.findByIdForUpdate(stoneCardReindexItemEntity.getStoneId());
        if ("CARD".equals(stoneCardReindexItemEntity.getWorkKind())) {
            if (stoneEntity.isPresent()) {
                LOG.debug("Enqueued forced index update for {}", stoneIndexEventService.publishUpdate(stoneEntity.get()).getId());
                stoneCardReindexItemEntity.setOutcome("ENQUEUED");
            } else {
                stoneCardReindexItemEntity.setOutcome("SKIPPED");
            }
        } else if (stoneEntity.isPresent()) {
            stoneCardReindexItemEntity.setOutcome("SKIPPED");
        } else {
            long sourceVersion = sourceVersionRepository.increment(new StoneIndexSourceVersionEntity(stoneCardReindexItemEntity.getStoneId(),
                    stateRepository.findByIdForUpdate(stoneCardReindexItemEntity.getStoneId()).sourceVersion())).sourceVersion();
            LOG.debug("Enqueued orphan recovery event {}", outboxService.save(new OutboxWriteRequest(UUID.randomUUID(),
                    StoneIndexEventType.STONE_CARD_INDEX_DELETED.name(), StoneIndexEventType.SCHEMA_VERSION,
                    jsonMapper.valueToTree(new StoneIndexDeletePayload(stoneCardReindexItemEntity.getStoneId(), sourceVersion)))).getEventId());
            stoneCardReindexItemEntity.setOutcome("RECONCILED");
        }
        return saveProgress(stoneCardReindexItemEntity);
    }

    public StoneCardReindexItemEntity recordError(StoneCardReindexItemEntity selectedItem, String error) {
        StoneCardReindexItemEntity stoneCardReindexItemEntity = itemRepository.findByIdForUpdate(selectedItem).orElseThrow();
        if (!"PENDING".equals(stoneCardReindexItemEntity.getOutcome())) return stoneCardReindexItemEntity;
        stoneCardReindexItemEntity.setOutcome("FAILED");
        stoneCardReindexItemEntity.setError(error);
        return saveProgress(stoneCardReindexItemEntity);
    }

    public StoneCardReindexJobEntity finishIfPrepared(UUID jobId) {
        if (controlRepository.findActiveJobIdForUpdate().filter(jobId::equals).isEmpty()) return jobRepository.findById(jobId).orElseThrow();
        StoneCardReindexJobEntity stoneCardReindexJobEntity = jobRepository.findById(jobId).orElseThrow();
        if (stoneCardReindexJobEntity.getProcessedCount() == stoneCardReindexJobEntity.getTotalCount()) {
            stoneCardReindexJobEntity.setStatus(StoneCardReindexStatus.ENQUEUED);
            stoneCardReindexJobEntity.setUpdatedAt(clock.instant());
            stoneCardReindexJobEntity = jobRepository.save(stoneCardReindexJobEntity);
            controlRepository.release(jobId);
        }
        return stoneCardReindexJobEntity;
    }

    private StoneCardReindexItemEntity savePendingItem(UUID jobId, long stoneId, String workKind) {
        StoneCardReindexItemEntity stoneCardReindexItemEntity = new StoneCardReindexItemEntity();
        stoneCardReindexItemEntity.setJobId(jobId);
        stoneCardReindexItemEntity.setStoneId(stoneId);
        stoneCardReindexItemEntity.setWorkKind(workKind);
        stoneCardReindexItemEntity.setOutcome("PENDING");
        return itemRepository.save(stoneCardReindexItemEntity);
    }

    private StoneCardReindexItemEntity saveProgress(StoneCardReindexItemEntity stoneCardReindexItemEntity) {
        StoneCardReindexItemEntity saved = itemRepository.save(stoneCardReindexItemEntity);
        StoneCardReindexJobEntity stoneCardReindexJobEntity = jobRepository.findById(saved.getJobId()).orElseThrow();
        stoneCardReindexJobEntity.setUpdatedAt(clock.instant());
        LOG.debug("Saved progress for reindex job {}", jobRepository.save(stoneCardReindexJobEntity).getJobId());
        return saved;
    }
}
