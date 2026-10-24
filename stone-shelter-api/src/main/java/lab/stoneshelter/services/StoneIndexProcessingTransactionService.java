package lab.stoneshelter.services;

import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.entities.StoneCardIndexStateEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.repositories.StoneCardIndexStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneIndexProcessingTransactionService {
    private final StoneCardIndexStateRepository stoneCardIndexStateRepository;
    private final StoneCardEmbeddingRepository stoneCardEmbeddingRepository;

    public StoneIndexProcessingTransactionService(StoneCardIndexStateRepository stoneCardIndexStateRepository,
            StoneCardEmbeddingRepository stoneCardEmbeddingRepository) {
        this.stoneCardIndexStateRepository = stoneCardIndexStateRepository;
        this.stoneCardEmbeddingRepository = stoneCardEmbeddingRepository;
    }

    @Transactional(readOnly = true)
    public boolean isStale(long stoneId, long sourceVersion) {
        return stoneCardIndexStateRepository.findById(stoneId).map(state -> sourceVersion <= state.sourceVersion()).orElse(false);
    }

    public boolean apply(StoneCardEmbeddingEntity stoneCardEmbeddingEntity, long sourceVersion) {
        if (sourceVersion <= stoneCardIndexStateRepository.findByIdForUpdate(stoneCardEmbeddingEntity.getStoneId()).sourceVersion()) return false;
        if (stoneCardEmbeddingRepository.hasIncompatibleProfile(stoneCardEmbeddingEntity.getEmbeddingProfile())) {
            throw new StoneDocumentDependencyUnavailableException("The stone-card index embedding profile is incompatible.");
        }
        return stoneCardIndexStateRepository.save(new StoneCardIndexStateEntity(
                stoneCardEmbeddingRepository.save(stoneCardEmbeddingEntity).getStoneId(), sourceVersion, false)).sourceVersion() == sourceVersion;
    }

    public boolean delete(long stoneId, long sourceVersion) {
        if (sourceVersion <= stoneCardIndexStateRepository.findByIdForUpdate(stoneId).sourceVersion()) return false;
        stoneCardEmbeddingRepository.deleteById(stoneId);
        return stoneCardIndexStateRepository.save(new StoneCardIndexStateEntity(stoneId, sourceVersion, true)).sourceVersion() == sourceVersion;
    }
}
