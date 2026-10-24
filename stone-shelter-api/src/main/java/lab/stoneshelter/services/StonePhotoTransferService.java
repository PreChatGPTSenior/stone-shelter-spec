package lab.stoneshelter.services;

import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StonePhotoTransferService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StonePhotoTransferService.class);
    private final StonePhotoEntityRepository repository;
    private final StonePhotoCopyService copyService;
    public StonePhotoTransferService(StonePhotoEntityRepository repository, StonePhotoCopyService copyService) {
        this.repository = repository;
        this.copyService = copyService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void copyAll(long stoneId) {
        try {
            for (long photoId : repository.findIdsByStoneId(stoneId)) {
                try {
                    copyService.copy(stoneId, photoId);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Permanent photo copy failed for stone {} photo {}; draft source retained", stoneId, photoId, exception);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Permanent photo copying could not start for stone {}; draft sources retained", stoneId, exception);
        }
    }
}
