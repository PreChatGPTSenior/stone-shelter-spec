package lab.stoneshelter.services;

import java.util.concurrent.atomic.AtomicBoolean;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import lab.stoneshelter.mappers.entities.OutboxEventEntityToOutboxDeadLetterEntityMapper;
import lab.stoneshelter.repositories.OutboxKafkaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableScheduling
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class OutboxDispatcherService {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcherService.class);
    private final OutboxDeliveryTransactionService outboxDeliveryTransactionService;
    private final OutboxKafkaRepository outboxKafkaRepository;
    private final OutboxEventRoutingService outboxEventRoutingService;
    private final OutboxEventEntityToOutboxDeadLetterEntityMapper outboxEventEntityToOutboxDeadLetterEntityMapper;
    private final OutboxKafkaTopicService outboxKafkaTopicService;
    private final AtomicBoolean brokerUnavailable = new AtomicBoolean();

    public OutboxDispatcherService(OutboxDeliveryTransactionService outboxDeliveryTransactionService,
            OutboxKafkaRepository outboxKafkaRepository, OutboxEventRoutingService outboxEventRoutingService,
            OutboxEventEntityToOutboxDeadLetterEntityMapper outboxEventEntityToOutboxDeadLetterEntityMapper,
            OutboxKafkaTopicService outboxKafkaTopicService) {
        this.outboxDeliveryTransactionService = outboxDeliveryTransactionService;
        this.outboxKafkaRepository = outboxKafkaRepository;
        this.outboxEventRoutingService = outboxEventRoutingService;
        this.outboxEventEntityToOutboxDeadLetterEntityMapper = outboxEventEntityToOutboxDeadLetterEntityMapper;
        this.outboxKafkaTopicService = outboxKafkaTopicService;
    }

    @Scheduled(fixedDelayString = "${OUTBOX_POLL_INTERVAL:PT2S}", initialDelayString = "${OUTBOX_POLL_INTERVAL:PT2S}")
    public synchronized void dispatch() {
        try {
            if (brokerUnavailable.get()) {
                if (!outboxKafkaRepository.isAvailable()) return;
                brokerUnavailable.set(false);
                outboxKafkaTopicService.invalidate();
            }
            outboxKafkaTopicService.ensureReady();
            for (OutboxEventEntity outboxEventEntity : outboxDeliveryTransactionService.findEligible()) {
                if (Thread.currentThread().isInterrupted()) return;
                if (!(outboxEventEntity.getDeliveryState() == OutboxDeliveryState.DEAD_LETTER_PENDING
                        ? transferDeadLetter(outboxEventEntity) : publish(outboxEventEntity))) return;
            }
        } catch (RuntimeException exception) {
            LOG.warn("Outbox dispatch is waiting for storage/control recovery: {}", exception.getClass().getSimpleName());
        }
    }

    private boolean publish(OutboxEventEntity outboxEventEntity) {
        String originalTopic = null;
        OutboxEventEntity acknowledgedOutboxEventEntity;
        try {
            OutboxDeliverySettings.Route route = outboxEventRoutingService.route(outboxEventEntity);
            originalTopic = route.topic();
            acknowledgedOutboxEventEntity = outboxKafkaRepository.publish(outboxEventEntity, route.topic(),
                    outboxEventRoutingService.key(outboxEventEntity, route));
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted() || !outboxKafkaRepository.isAvailable()) {
                brokerUnavailable.set(true);
                outboxKafkaTopicService.invalidate();
                return false;
            }
            OutboxEventEntity failedOutboxEventEntity = outboxDeliveryTransactionService.recordFailure(outboxEventEntity,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(), originalTopic);
            LOG.warn("Outbox event {} charged attempt {} and entered {}", failedOutboxEventEntity.getEventId(),
                    failedOutboxEventEntity.getAttemptCount(), failedOutboxEventEntity.getDeliveryState());
            return true;
        }
        LOG.debug("Published outbox event {}", outboxDeliveryTransactionService.markPublished(
                acknowledgedOutboxEventEntity.getEventId()).getEventId());
        return true;
    }

    private boolean transferDeadLetter(OutboxEventEntity outboxEventEntity) {
        OutboxDeadLetterEntity acknowledgedOutboxDeadLetterEntity;
        try {
            acknowledgedOutboxDeadLetterEntity = outboxKafkaRepository.publishDeadLetter(
                    outboxEventEntityToOutboxDeadLetterEntityMapper.toEntity(outboxEventEntity),
                    OutboxDeliverySettings.DEAD_LETTER_TOPIC, outboxEventEntity.getEventId().toString());
        } catch (RuntimeException exception) {
            outboxKafkaTopicService.invalidate();
            LOG.warn("Dead-letter transfer for {} remains pending", outboxDeliveryTransactionService.deferDeadLetter(
                    outboxEventEntity.getEventId()).getEventId());
            boolean available = !Thread.currentThread().isInterrupted() && outboxKafkaRepository.isAvailable();
            brokerUnavailable.set(!available);
            return available;
        }
        LOG.info("Dead-lettered {} at stage {} after {} charged attempts", outboxDeliveryTransactionService.markDeadLettered(
                outboxEventEntity.getEventId()).getEventId(), acknowledgedOutboxDeadLetterEntity.getFailureStage(),
                acknowledgedOutboxDeadLetterEntity.getAttemptCount());
        return true;
    }
}
