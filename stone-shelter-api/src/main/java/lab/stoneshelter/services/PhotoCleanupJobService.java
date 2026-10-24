package lab.stoneshelter.services;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.exceptions.PhotoStorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PhotoCleanupJobService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhotoCleanupJobService.class);
    private final PhotoCleanupService cleanup;
    private final MinioPhotoStorageService storage;
    private final int batchSize;
    private final int maxObjects;
    private final long intervalMillis;
    private final Duration maxDuration;

    public PhotoCleanupJobService(PhotoCleanupService cleanup, MinioPhotoStorageService storage,
            @Value("${stone.photos.cleanup-batch-size}") int batchSize,
            @Value("${stone.photos.cleanup-max-objects}") int maxObjects,
            @Value("${stone.photos.cleanup-interval-ms}") long intervalMillis,
            @Value("${stone.photos.cleanup-max-duration}") Duration maxDuration) {
        if (batchSize < 1 || maxObjects < 1 || intervalMillis < 0 || maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("Photo cleanup limits must be positive; pacing may be zero.");
        }
        this.cleanup = cleanup;
        this.storage = storage;
        this.batchSize = batchSize;
        this.maxObjects = maxObjects;
        this.intervalMillis = intervalMillis;
        this.maxDuration = maxDuration;
    }

    public void run() {
        long startedAt = System.nanoTime();
        int processed = 0;
        try {
            if (Thread.currentThread().isInterrupted()) return;
            storage.checkAvailability();
            boolean draftBucket = false;
            while (processed < maxObjects && System.nanoTime() - startedAt < maxDuration.toNanos()) {
                var objectKeys = draftBucket
                        ? cleanup.findDueDraftKeys(Math.min(batchSize, maxObjects - processed))
                        : cleanup.findDueKeys(Math.min(batchSize, maxObjects - processed));
                if (objectKeys.isEmpty()) {
                    if (draftBucket) return;
                    draftBucket = true;
                    continue;
                }
                for (String objectKey : objectKeys) {
                    if (processed > 0 && intervalMillis > 0) {
                        if (TimeUnit.MILLISECONDS.toNanos(intervalMillis)
                                >= maxDuration.toNanos() - (System.nanoTime() - startedAt)) return;
                        Thread.sleep(intervalMillis);
                    }
                    if (Thread.currentThread().isInterrupted()
                            || System.nanoTime() - startedAt >= maxDuration.toNanos()) return;
                    if (draftBucket) cleanup.cleanDraft(objectKey);
                    else cleanup.clean(objectKey);
                    processed++;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Daily photo cleanup interrupted after {} intents; remaining work retained", processed);
        } catch (PhotoStorageUnavailableException exception) {
            LOGGER.warn("Daily photo cleanup stopped: storage unavailable; {} intents processed", processed);
        } catch (RuntimeException exception) {
            LOGGER.warn("Daily photo cleanup stopped after {} intents; remaining work retained", processed, exception);
        }
    }
}
