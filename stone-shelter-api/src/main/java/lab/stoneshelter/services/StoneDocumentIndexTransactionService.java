package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.criteria.StoneDocumentIndexOperationCriteria;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneDocumentIndexOperationEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;
import lab.stoneshelter.exceptions.StoneDocumentIndexConflictException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.entities.StoneDocumentIndexOperationCriteriaToStoneDocumentIndexOperationEntityMapper;
import lab.stoneshelter.repositories.StoneDocumentIndexRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneDocumentIndexTransactionService {
    private final StoneDocumentIndexRepository repository;
    private final StoneDocumentIndexOperationCriteriaToStoneDocumentIndexOperationEntityMapper mapper;
    private final Clock clock;

    @Autowired
    public StoneDocumentIndexTransactionService(StoneDocumentIndexRepository repository,
            StoneDocumentIndexOperationCriteriaToStoneDocumentIndexOperationEntityMapper mapper) {
        this(repository, mapper, Clock.systemUTC());
    }

    public StoneDocumentIndexTransactionService(StoneDocumentIndexRepository repository,
            StoneDocumentIndexOperationCriteriaToStoneDocumentIndexOperationEntityMapper mapper, Clock clock) {
        this.repository = repository; this.mapper = mapper; this.clock = clock;
    }

    public Instant now() { return clock.instant(); }

    public StoneDocumentIndexOperationEntity claim(StoneDocumentIndexOperationCriteria stoneDocumentIndexOperationCriteria) {
        StoneDocumentIndexOperationEntity existingStoneDocumentIndexOperationEntity = repository.findOperation(stoneDocumentIndexOperationCriteria.idempotencyKey()).orElse(null);
        if (existingStoneDocumentIndexOperationEntity == null) {
            lock();
            existingStoneDocumentIndexOperationEntity = repository.findOperation(stoneDocumentIndexOperationCriteria.idempotencyKey()).orElse(null);
        }
        if (existingStoneDocumentIndexOperationEntity != null) {
            if (existingStoneDocumentIndexOperationEntity.getKind() != stoneDocumentIndexOperationCriteria.kind() || !existingStoneDocumentIndexOperationEntity.getDocumentKey().equals(StoneDocumentIndexEntity.DOCUMENT_KEY)) {
                throw new StoneDocumentIndexConflictException("The key belongs to a different operation or document.");
            }
            if (existingStoneDocumentIndexOperationEntity.getState() == StoneDocumentIndexOperationState.RUNNING) busy();
            return expire(existingStoneDocumentIndexOperationEntity);
        }
        repository.ensureAnchor();
        if (repository.findById(StoneDocumentIndexEntity.DOCUMENT_KEY).orElseThrow().getActiveOperationKey() != null) busy();
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity = repository.insertOperation(mapper.toEntity(stoneDocumentIndexOperationCriteria));
        if (!repository.claimOwner(stoneDocumentIndexOperationEntity)) busy();
        return stoneDocumentIndexOperationEntity;
    }

    public StoneDocumentIndexOperationEntity publish(UUID key, UUID owner, List<StoneDocumentFragmentEntity> fragments) {
        lock();
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity = owned(key, owner);
        if (!now().isBefore(stoneDocumentIndexOperationEntity.getDeadline()) || Thread.currentThread().isInterrupted()) {
            throw new StoneDocumentDependencyUnavailableException("The document operation deadline has expired.");
        }
        StoneDocumentIndexEntity stoneDocumentIndexEntity = repository.findById(stoneDocumentIndexOperationEntity.getDocumentKey()).orElseThrow();
        repository.deleteFragments(stoneDocumentIndexEntity.getDocumentKey());
        if (stoneDocumentIndexOperationEntity.getKind() == StoneDocumentIndexOperationKind.IMPORT) {
            if (fragments.isEmpty()) throw new IllegalArgumentException("Complete prepared fragments are required.");
            stoneDocumentIndexOperationEntity.setOutcome(stoneDocumentIndexEntity.getSourceVersion() == null ? StoneDocumentIndexOutcome.CREATED : StoneDocumentIndexOutcome.REPLACED);
            stoneDocumentIndexOperationEntity.setSourceVersion(fragments.getFirst().getSourceVersion());
            stoneDocumentIndexOperationEntity.setIndexedFragments(fragments.size());
            stoneDocumentIndexEntity.setSourceVersion(stoneDocumentIndexOperationEntity.getSourceVersion());
            stoneDocumentIndexEntity.setEmbeddingProfile(fragments.getFirst().getEmbeddingProfile());
            stoneDocumentIndexEntity.setLanguage("en");
            stoneDocumentIndexEntity.setIndexedFragments(fragments.size());
            repository.save(stoneDocumentIndexEntity);
            repository.insertFragments(fragments);
        } else {
            stoneDocumentIndexEntity.setSourceVersion(null); stoneDocumentIndexEntity.setEmbeddingProfile(null); stoneDocumentIndexEntity.setLanguage(null); stoneDocumentIndexEntity.setIndexedFragments(0);
            repository.save(stoneDocumentIndexEntity);
        }
        stoneDocumentIndexOperationEntity.setState(StoneDocumentIndexOperationState.SUCCEEDED);
        stoneDocumentIndexOperationEntity.setCommittedAt(now());
        stoneDocumentIndexOperationEntity.setReplayUntil(now().plus(Duration.ofDays(3)));
        stoneDocumentIndexOperationEntity.setOwnerToken(null); stoneDocumentIndexOperationEntity.setDeadline(null);
        repository.updateOperation(stoneDocumentIndexOperationEntity);
        repository.clearOwner(key, owner);
        return stoneDocumentIndexOperationEntity;
    }

    public StoneDocumentIndexOperationEntity reconcileStopped(UUID key, UUID verifiedStoppedOwner) {
        if (verifiedStoppedOwner == null) throw new StoneDocumentIndexConflictException("The prior worker must be verified stopped.");
        lock();
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity = repository.findOperation(key).orElseThrow(
                () -> new StoneDocumentIndexConflictException("The operation outcome is unknown."));
        if (stoneDocumentIndexOperationEntity.getState() != StoneDocumentIndexOperationState.RUNNING) return expire(stoneDocumentIndexOperationEntity);
        owned(key, verifiedStoppedOwner);
        stoneDocumentIndexOperationEntity.setState(StoneDocumentIndexOperationState.FAILED);
        stoneDocumentIndexOperationEntity.setOwnerToken(null); stoneDocumentIndexOperationEntity.setDeadline(null);
        repository.updateOperation(stoneDocumentIndexOperationEntity);
        repository.clearOwner(key, verifiedStoppedOwner);
        return stoneDocumentIndexOperationEntity;
    }

    private StoneDocumentIndexOperationEntity expire(StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity) {
        if (stoneDocumentIndexOperationEntity.getState() == StoneDocumentIndexOperationState.SUCCEEDED && !now().isBefore(stoneDocumentIndexOperationEntity.getReplayUntil())) {
            stoneDocumentIndexOperationEntity.setState(StoneDocumentIndexOperationState.EXPIRED);
            stoneDocumentIndexOperationEntity.setSourceVersion(null); stoneDocumentIndexOperationEntity.setIndexedFragments(null); stoneDocumentIndexOperationEntity.setOutcome(null);
            stoneDocumentIndexOperationEntity.setCommittedAt(null); stoneDocumentIndexOperationEntity.setReplayUntil(null);
            repository.updateOperation(stoneDocumentIndexOperationEntity);
        }
        return stoneDocumentIndexOperationEntity;
    }

    private StoneDocumentIndexOperationEntity owned(UUID key, UUID owner) {
        return repository.findOwnedOperation(key, owner).orElseThrow(
                () -> new StoneDocumentIndexConflictException("The worker no longer owns this operation."));
    }

    private void lock() { if (!repository.tryMutationLock()) busy(); }
    private void busy() { throw new StoneDocumentIndexConflictException("This operation is already running."); }
}
