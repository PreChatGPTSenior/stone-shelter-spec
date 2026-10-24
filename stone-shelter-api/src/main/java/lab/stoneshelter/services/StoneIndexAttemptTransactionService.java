package lab.stoneshelter.services;

import java.time.Clock;
import java.util.List;
import lab.stoneshelter.entities.StoneCardIndexAttemptEntity;
import lab.stoneshelter.repositories.StoneCardIndexAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneIndexAttemptTransactionService {
    private final StoneCardIndexAttemptRepository stoneCardIndexAttemptRepository;
    private final Clock clock;

    public StoneIndexAttemptTransactionService(StoneCardIndexAttemptRepository stoneCardIndexAttemptRepository, Clock clock) {
        this.stoneCardIndexAttemptRepository = stoneCardIndexAttemptRepository;
        this.clock = clock;
    }

    public StoneCardIndexAttemptEntity recordFailure(StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity, String reason, boolean invalidContract) {
        stoneCardIndexAttemptEntity.setLastError(reason);
        if (!invalidContract) stoneCardIndexAttemptEntity.setAttemptCount(stoneCardIndexAttemptEntity.getAttemptCount() + 1);
        stoneCardIndexAttemptEntity.setDeadLetterPending(invalidContract || stoneCardIndexAttemptEntity.getAttemptCount() >= 3);
        stoneCardIndexAttemptEntity.setNextAttemptAt(clock.instant().plusSeconds(stoneCardIndexAttemptEntity.getDeadLetterPending()
                ? 0 : stoneCardIndexAttemptEntity.getAttemptCount() == 1 ? 2 : 4));
        return stoneCardIndexAttemptRepository.save(stoneCardIndexAttemptEntity);
    }

    public StoneCardIndexAttemptEntity markDeadLettered(StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity) {
        stoneCardIndexAttemptEntity.setDeadLetterPending(false);
        stoneCardIndexAttemptEntity.setDeadLettered(true);
        stoneCardIndexAttemptEntity.setNextAttemptAt(null);
        return stoneCardIndexAttemptRepository.save(stoneCardIndexAttemptEntity);
    }

    public StoneCardIndexAttemptEntity deferDeadLetter(StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity) {
        stoneCardIndexAttemptEntity.setNextAttemptAt(clock.instant().plusSeconds(2));
        return stoneCardIndexAttemptRepository.save(stoneCardIndexAttemptEntity);
    }

    public void deleteById(String topic, int partition, long offset) {
        stoneCardIndexAttemptRepository.deleteById(topic, partition, offset);
    }

    @Transactional(readOnly = true)
    public List<StoneCardIndexAttemptEntity> findPendingDeadLetters() {
        return stoneCardIndexAttemptRepository.findPendingDeadLetters(clock.instant());
    }
}
