package lab.stoneshelter.services;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneIndexConsumerService {
    public static final String GROUP = "stone-card-indexer";
    private static final Logger LOG = LoggerFactory.getLogger(StoneIndexConsumerService.class);
    private final StoneIndexProcessingService stoneIndexProcessingService;
    private final Clock clock;
    private final KafkaMessageListenerContainer<String, byte[]> container;

    public StoneIndexConsumerService(ConsumerFactory<?, ?> consumerFactory,
            StoneIndexProcessingService stoneIndexProcessingService, Clock clock) {
        this.stoneIndexProcessingService = stoneIndexProcessingService;
        this.clock = clock;
        ContainerProperties containerProperties = new ContainerProperties(StoneIndexKafkaTopicService.TOPIC);
        containerProperties.setGroupId(GROUP);
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setPollTimeout(100);
        containerProperties.setPollTimeoutWhilePaused(Duration.ofMillis(100));
        containerProperties.setMessageListener((AcknowledgingMessageListener<String, byte[]>) this::receive);
        container = new KafkaMessageListenerContainer<>(new DefaultKafkaConsumerFactory<String, byte[]>(
                consumerConfiguration(consumerFactory.getConfigurationProperties())), containerProperties);
        container.setBeanName("stone-card-indexer");
    }

    public Map<String, Object> consumerConfiguration(Map<String, Object> base) {
        Map<String, Object> configuration = new HashMap<>(base);
        configuration.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP);
        configuration.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configuration.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        configuration.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configuration.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return configuration;
    }

    @Autowired
    public void configurePollDeadline(Environment environment) {
        // The embedding client bounds each call; paused retries continue polling.
        container.getContainerProperties().setKafkaConsumerProperties(new java.util.Properties());
        container.getContainerProperties().getKafkaConsumerProperties().setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                Long.toString(Duration.parse(environment.getProperty("STONE_DOCUMENT_EMBEDDING_TIMEOUT", "PT30M")).plusMinutes(1).toMillis()));
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void recoverPendingDeadLetters() { stoneIndexProcessingService.recoverPendingDeadLetters(); }

    @EventListener(ApplicationReadyEvent.class)
    public void start() { container.start(); }

    @PreDestroy
    public void stop() { container.stop(); }

    public void receive(ConsumerRecord<String, byte[]> consumerRecord, Acknowledgment acknowledgment) {
        try {
            StoneIndexProcessingService.Outcome outcome = stoneIndexProcessingService.processRecord(consumerRecord.topic(), consumerRecord.partition(),
                    consumerRecord.offset(), consumerRecord.value(), consumerRecord.key());
            if (outcome.completed()) {
                acknowledgment.acknowledge();
            } else {
                Duration delay = Duration.between(clock.instant(), outcome.retryAt());
                acknowledgment.nack(delay.isNegative() || delay.isZero() ? Duration.ofMillis(100) : delay);
            }
        } catch (RuntimeException exception) {
            LOG.warn("Index partition waits after {}:{}", consumerRecord.partition(), consumerRecord.offset());
            acknowledgment.nack(Duration.ofSeconds(2));
        }
    }
}
