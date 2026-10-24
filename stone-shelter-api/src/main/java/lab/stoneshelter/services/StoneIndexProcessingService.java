package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import lab.stoneshelter.entities.StoneCardIndexAttemptEntity;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.repositories.StoneCardIndexAttemptRepository;
import lab.stoneshelter.repositories.OutboxKafkaRepository;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import lab.stoneshelter.enums.StoneIndexEventType;
import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneIndexProcessingService {
    private static final Logger LOG = LoggerFactory.getLogger(StoneIndexProcessingService.class);
    private StoneCardIndexAttemptRepository stoneCardIndexAttemptRepository;
    private ObjectProvider<StoneIndexAttemptTransactionService> attemptTransactions;
    private ObjectProvider<OutboxKafkaRepository> kafkaRepository;
    public record Outcome(boolean completed, Instant retryAt) {}
    private final StoneIndexProcessingTransactionService stoneIndexProcessingTransactionService;
    private final StoneCardEmbeddingService stoneCardEmbeddingService;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public StoneIndexProcessingService(StoneIndexProcessingTransactionService stoneIndexProcessingTransactionService,
            StoneCardEmbeddingService stoneCardEmbeddingService, Clock clock) {
        this.stoneIndexProcessingTransactionService = stoneIndexProcessingTransactionService;
        this.stoneCardEmbeddingService = stoneCardEmbeddingService;
        this.clock = clock;
    }

    @Autowired
    public void configureRetryServices(StoneCardIndexAttemptRepository stoneCardIndexAttemptRepository,
            ObjectProvider<StoneIndexAttemptTransactionService> attemptTransactions, ObjectProvider<OutboxKafkaRepository> kafkaRepository) {
        this.stoneCardIndexAttemptRepository = stoneCardIndexAttemptRepository;
        this.attemptTransactions = attemptTransactions;
        this.kafkaRepository = kafkaRepository;
    }

    public synchronized Outcome processRecord(String topic, int partition, long offset, byte[] originalRecord, String key) {
        try {
            StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity = stoneCardIndexAttemptRepository.findById(topic, partition, offset)
                    .orElseGet(() -> newAttempt(topic, partition, offset, originalRecord));
            if (stoneCardIndexAttemptEntity.getDeadLettered()) return new Outcome(true, clock.instant());
            if (stoneCardIndexAttemptEntity.getNextAttemptAt() != null && stoneCardIndexAttemptEntity.getNextAttemptAt().isAfter(clock.instant())) {
                return new Outcome(false, stoneCardIndexAttemptEntity.getNextAttemptAt());
            }
            if (stoneCardIndexAttemptEntity.getDeadLetterPending()) return transferDeadLetter(stoneCardIndexAttemptEntity);
            try {
                Outcome outcome = process(originalRecord, key);
                if (stoneCardIndexAttemptEntity.getAttemptCount() > 0) {
                    attemptTransactions.getObject().deleteById(topic, partition, offset);
                }
                return outcome;
            } catch (OutboxValidationException exception) {
                return transferDeadLetter(attemptTransactions.getObject().recordFailure(stoneCardIndexAttemptEntity, reason(exception), true));
            } catch (ModelAdmissionDeferredException exception) {
                return new Outcome(false, exception.getRetryAt());
            } catch (StoneDocumentDependencyUnavailableException | DataAccessException exception) {
                return waiting();
            } catch (RuntimeException exception) {
                OutboxKafkaRepository outboxKafkaRepository = kafkaRepository.getIfAvailable();
                if (outboxKafkaRepository == null || Thread.currentThread().isInterrupted() || !outboxKafkaRepository.isAvailable()) return waiting();
                StoneCardIndexAttemptEntity failed = attemptTransactions.getObject().recordFailure(stoneCardIndexAttemptEntity, reason(exception), false);
                LOG.warn("Index record {}:{}:{} charged attempt {}", topic, partition, offset, failed.getAttemptCount());
                return failed.getDeadLetterPending() ? transferDeadLetter(failed) : new Outcome(false, failed.getNextAttemptAt());
            }
        } catch (RuntimeException exception) {
            LOG.warn("Index partition waits for delivery/control storage recovery: {}", exception.getClass().getSimpleName());
            return waiting();
        }
    }

    public synchronized void recoverPendingDeadLetters() {
        try {
            for (StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity : attemptTransactions.getObject().findPendingDeadLetters()) {
                if (!transferDeadLetter(stoneCardIndexAttemptEntity).completed()) return;
            }
        } catch (RuntimeException exception) {
            LOG.warn("Durable index DLQ recovery remains pending: {}", exception.getClass().getSimpleName());
        }
    }

    private Outcome transferDeadLetter(StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity) {
        try {
            OutboxDeadLetterEntity outboxDeadLetterEntity = OutboxDeadLetterEntity.fromConsumer(stoneCardIndexAttemptEntity);
            String key = stoneCardIndexAttemptEntity.getSourceTopic() + ":" + stoneCardIndexAttemptEntity.getSourcePartition()
                    + ":" + stoneCardIndexAttemptEntity.getSourceOffset();
            try {
                key = UUID.fromString(outboxDeadLetterEntity.getOriginalEvent().path("eventId").asString()).toString();
            } catch (RuntimeException exception) {
                // Invalid records retain the source coordinate as their delivery identity.
            }
            OutboxDeadLetterEntity acknowledged = kafkaRepository.getObject().publishDeadLetter(outboxDeadLetterEntity,
                    OutboxDeliverySettings.DEAD_LETTER_TOPIC, key);
            LOG.info("Index record {}:{}:{} dead-lettered at {}", stoneCardIndexAttemptEntity.getSourceTopic(),
                    stoneCardIndexAttemptEntity.getSourcePartition(), stoneCardIndexAttemptEntity.getSourceOffset(), acknowledged.getFailureStage());
            return new Outcome(attemptTransactions.getObject().markDeadLettered(stoneCardIndexAttemptEntity).getDeadLettered(), clock.instant());
        } catch (RuntimeException exception) {
            return new Outcome(false, attemptTransactions.getObject().deferDeadLetter(stoneCardIndexAttemptEntity).getNextAttemptAt());
        }
    }

    private StoneCardIndexAttemptEntity newAttempt(String topic, int partition, long offset, byte[] originalRecord) {
        StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity = new StoneCardIndexAttemptEntity();
        stoneCardIndexAttemptEntity.setSourceTopic(topic);
        stoneCardIndexAttemptEntity.setSourcePartition(partition);
        stoneCardIndexAttemptEntity.setSourceOffset(offset);
        stoneCardIndexAttemptEntity.setOriginalRecord(originalRecord);
        return stoneCardIndexAttemptEntity;
    }

    private Outcome waiting() { return new Outcome(false, clock.instant().plusSeconds(2)); }
    private String reason(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public Outcome process(byte[] originalRecord, String key) {
        JsonNode event = validate(originalRecord, key);
        JsonNode payload = event.get("payload");
        long stoneId = payload.get("stoneId").asLong();
        long sourceVersion = payload.get("sourceVersion").asLong();
        if (!stoneIndexProcessingTransactionService.isStale(stoneId, sourceVersion)) {
            if (event.get("eventType").asString().equals(StoneIndexEventType.STONE_CARD_INDEX_DELETED.name())) {
                stoneIndexProcessingTransactionService.delete(stoneId, sourceVersion);
            } else {
                stoneIndexProcessingTransactionService.apply(stoneCardEmbeddingService.prepare(
                        jsonMapper.treeToValue(payload, StoneCardSaveRequest.class)), sourceVersion);
            }
        }
        return new Outcome(true, clock.instant());
    }

    public JsonNode validate(byte[] originalRecord, String key) {
        try {
            JsonNode event = jsonMapper.readTree(originalRecord);
            if (event == null || !event.isObject() || !Set.copyOf(event.propertyNames()).equals(Set.of("eventId", "eventType", "schemaVersion", "occurredAt", "payload"))
                    || !event.path("eventId").isString() || !event.path("occurredAt").isString()
                    || !event.path("schemaVersion").isIntegralNumber() || !event.path("schemaVersion").canConvertToInt() || event.path("schemaVersion").asInt() != 1) {
                throw new OutboxValidationException("Invalid index event envelope.");
            }
            if (!UUID.fromString(event.get("eventId").asString()).toString().equals(event.get("eventId").asString())) {
                throw new OutboxValidationException("Invalid event UUID.");
            }
            Instant.parse(event.get("occurredAt").asString());
            StoneIndexEventType stoneIndexEventType = StoneIndexEventType.valueOf(event.path("eventType").asString());
            JsonNode payload = event.path("payload");
            Set<String> fields = stoneIndexEventType == StoneIndexEventType.STONE_CARD_INDEX_DELETED
                    ? Set.of("stoneId", "sourceVersion") : Set.of("stoneId", "sourceVersion", "name", "stoneType", "stoneSize", "biography");
            if (!payload.isObject() || !Set.copyOf(payload.propertyNames()).equals(fields)
                    || !positiveLong(payload.path("stoneId")) || !positiveLong(payload.path("sourceVersion"))
                    || !Long.toString(payload.get("stoneId").asLong()).equals(key)) {
                throw new OutboxValidationException("Invalid index event identity or fields.");
            }
            if (stoneIndexEventType == StoneIndexEventType.STONE_CARD_INDEX_UPDATED) {
                StoneCardSaveRequest stoneCardSaveRequest = jsonMapper.treeToValue(payload, StoneCardSaveRequest.class);
                if (!payload.path("name").isString() || stoneCardSaveRequest.getName().isBlank()
                        || stoneCardSaveRequest.getName().length() > 120 || stoneCardSaveRequest.getStoneType() == null
                        || stoneCardSaveRequest.getStoneSize() == null || !(payload.path("biography").isNull()
                        || (payload.path("biography").isString() && stoneCardSaveRequest.getBiography().length() <= 2048))) {
                    throw new OutboxValidationException("Invalid index update snapshot.");
                }
            }
            return event;
        } catch (OutboxValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OutboxValidationException("Invalid index event contract.", exception);
        }
    }

    private boolean positiveLong(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0;
    }
}
