package lab.stoneshelter.services;

import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneCardEmbeddingTransactionService {
    private final StoneCardEmbeddingRepository stoneCardEmbeddingRepository;

    public StoneCardEmbeddingTransactionService(StoneCardEmbeddingRepository stoneCardEmbeddingRepository) {
        this.stoneCardEmbeddingRepository = stoneCardEmbeddingRepository;
    }

    public StoneCardEmbeddingEntity save(StoneCardEmbeddingEntity stoneCardEmbeddingEntity) {
        return stoneCardEmbeddingRepository.save(stoneCardEmbeddingEntity);
    }
}
