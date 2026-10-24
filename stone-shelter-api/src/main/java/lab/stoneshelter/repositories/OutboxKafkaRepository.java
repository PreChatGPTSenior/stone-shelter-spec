package lab.stoneshelter.repositories;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.exceptions.OutboxWriteException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class OutboxKafkaRepository {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OutboxKafkaRepository(KafkaTemplate<String, String> kafkaTemplate, KafkaAdmin kafkaAdmin) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        kafkaTemplate.getProducerFactory().updateConfigs(Map.of(
                ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    public OutboxEventEntity publish(OutboxEventEntity outboxEventEntity, String topic, String key) {
        ObjectNode envelope = jsonMapper.createObjectNode()
                .put("eventId", outboxEventEntity.getEventId().toString())
                .put("eventType", outboxEventEntity.getEventType())
                .put("schemaVersion", outboxEventEntity.getSchemaVersion())
                .put("occurredAt", outboxEventEntity.getOccurredAt().toString());
        envelope.set("payload", outboxEventEntity.getPayload());
        acknowledge(topic, key, jsonMapper.writeValueAsString(envelope));
        return outboxEventEntity;
    }

    public OutboxDeadLetterEntity publishDeadLetter(OutboxDeadLetterEntity outboxDeadLetterEntity, String topic, String key) {
        acknowledge(topic, key, jsonMapper.writeValueAsString(outboxDeadLetterEntity));
        return outboxDeadLetterEntity;
    }

    public boolean isAvailable() {
        Map<String, Object> properties = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
        Admin admin = null;
        try {
            admin = Admin.create(properties);
            return !admin.describeCluster().nodes().get(2, TimeUnit.SECONDS).isEmpty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException | ExecutionException | TimeoutException exception) {
            return false;
        } finally {
            if (admin != null) admin.close(Duration.ZERO);
        }
    }

    private void acknowledge(String topic, String key, String value) {
        try {
            kafkaTemplate.send(topic, key, value).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxWriteException("Kafka publication was interrupted before acknowledgement.", exception);
        } catch (RuntimeException | ExecutionException | TimeoutException exception) {
            throw new OutboxWriteException("Kafka publication was not acknowledged.", exception);
        }
    }
}
