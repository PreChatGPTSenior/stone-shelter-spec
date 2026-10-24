package lab.stoneshelter.services;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.stoneshelter.enums.StoneIndexEventType;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneIndexKafkaTopicService {
    public static final String TOPIC = "stone-card-index-events";
    private static final Logger LOG = LoggerFactory.getLogger(StoneIndexKafkaTopicService.class);
    private final KafkaAdmin kafkaAdmin;
    private final AtomicBoolean ready = new AtomicBoolean();

    public StoneIndexKafkaTopicService(KafkaAdmin kafkaAdmin, OutboxDeliverySettings outboxDeliverySettings) {
        this.kafkaAdmin = kafkaAdmin;
        for (StoneIndexEventType stoneIndexEventType : StoneIndexEventType.values()) {
            LOG.debug("Registered index route {}", outboxDeliverySettings.registerRoute(stoneIndexEventType.name(), TOPIC, "stoneId").topic());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { ensureReady(); }

    @Scheduled(fixedDelayString = "${OUTBOX_POLL_INTERVAL:PT2S}", initialDelayString = "${OUTBOX_POLL_INTERVAL:PT2S}")
    public void ensureReady() {
        if (ready.get()) return;
        try {
            kafkaAdmin.createOrModifyTopics(new NewTopic(TOPIC, 1, (short) 1).configs(Map.of(
                    TopicConfig.RETENTION_MS_CONFIG, "86400000", TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)));
            ready.set(true);
        } catch (RuntimeException exception) {
            LOG.warn("Index topic provisioning is waiting for recovery: {}", exception.getClass().getSimpleName());
        }
    }
}
