package lab.stoneshelter.services;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class OutboxKafkaTopicService {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxKafkaTopicService.class);
    private final KafkaAdmin kafkaAdmin;
    private final OutboxDeliverySettings outboxDeliverySettings;
    private final AtomicBoolean ready = new AtomicBoolean();

    public OutboxKafkaTopicService(KafkaAdmin kafkaAdmin, OutboxDeliverySettings outboxDeliverySettings) {
        this.kafkaAdmin = kafkaAdmin;
        this.outboxDeliverySettings = outboxDeliverySettings;
        kafkaAdmin.setOperationTimeout(3);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { ensureReady(); }

    public void invalidate() { ready.set(false); }

    public boolean ensureReady() {
        if (ready.get()) return true;
        try {
            kafkaAdmin.createOrModifyTopics(new NewTopic(OutboxDeliverySettings.DEAD_LETTER_TOPIC, 1, (short) 1)
                    .configs(Map.of(TopicConfig.RETENTION_MS_CONFIG, Long.toString(outboxDeliverySettings.deadLetterRetention().toMillis()),
                            TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)));
            ready.set(true);
            return true;
        } catch (RuntimeException exception) {
            LOG.warn("Dead-letter topic provisioning is waiting for recovery: {}", exception.toString());
            return false;
        }
    }
}
