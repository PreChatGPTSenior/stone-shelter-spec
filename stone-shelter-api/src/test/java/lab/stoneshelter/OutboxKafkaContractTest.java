package lab.stoneshelter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.mappers.entities.OutboxEventEntityToOutboxDeadLetterEntityMapper;
import lab.stoneshelter.repositories.OutboxKafkaRepository;
import lab.stoneshelter.services.OutboxDeliverySettings;
import lab.stoneshelter.services.OutboxEventRoutingService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxKafkaContractTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;
    private ProducerFactory<String, String> producerFactory;
    private OutboxKafkaRepository outboxKafkaRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepareProducer() {
        kafkaTemplate = mock(KafkaTemplate.class);
        producerFactory = mock(ProducerFactory.class);
        when(kafkaTemplate.getProducerFactory()).thenReturn(producerFactory);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        outboxKafkaRepository = new OutboxKafkaRepository(kafkaTemplate, mock(KafkaAdmin.class));
    }

    @Test
    void normalEnvelopeAndProducerGuaranteesMatchAsyncApi() throws Exception {
        verify(producerFactory).updateConfigs(Map.of(ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        OutboxEventEntity outboxEventEntity = event();
        OutboxEventRoutingService routing = new OutboxEventRoutingService(new OutboxDeliverySettings(new MockEnvironment()
                .withProperty("OUTBOX_ROUTES", "{\"event\":{\"topic\":\"events\"}}")));
        OutboxDeliverySettings.Route route = routing.route(outboxEventEntity);
        assertThat(outboxKafkaRepository.publish(outboxEventEntity, route.topic(), routing.key(outboxEventEntity, route))).isSameAs(outboxEventEntity);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("events"), eq(outboxEventEntity.getEventId().toString()), value.capture());
        JsonNode envelope = jsonMapper.readTree(value.getValue());
        assertRequiredFields(envelope, "EventEnvelope");
        assertThat(envelope.path("eventId").asText()).isEqualTo(outboxEventEntity.getEventId().toString());
        assertThat(envelope.path("eventType").asText()).isEqualTo("event");
        assertThat(envelope.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.path("occurredAt").asText()).isEqualTo(outboxEventEntity.getOccurredAt().toString());
        assertThat(envelope.path("payload")).isEqualTo(outboxEventEntity.getPayload());
    }

    @Test
    void waitsForAcknowledgementAndSurfacesFailedAcknowledgements() throws Exception {
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            invoked.countDown();
            return acknowledgement;
        });
        var executorService = Executors.newSingleThreadExecutor();
        try {
            OutboxEventEntity outboxEventEntity = event();
            var published = executorService.submit(() -> outboxKafkaRepository.publish(outboxEventEntity, "events", "key"));
            assertThat(invoked.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(published.isDone()).isFalse();
            acknowledgement.complete(null);
            assertThat(published.get(1, TimeUnit.SECONDS)).isSameAs(outboxEventEntity);
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("event cannot be published")));
        assertThatThrownBy(() -> outboxKafkaRepository.publish(event(), "events", "key"))
                .isInstanceOf(OutboxWriteException.class).hasRootCauseInstanceOf(KafkaException.class);
    }

    @Test
    void deadLetterPreservesOriginalEnvelopeAndOriginalFailureMetadata() throws Exception {
        OutboxEventEntity outboxEventEntity = event();
        outboxEventEntity.setAttemptCount(3);
        outboxEventEntity.setLastError("{\"message\":\"invalid event\",\"originalTopic\":\"events\"}");
        OutboxEventEntityToOutboxDeadLetterEntityMapper mapper = new OutboxEventEntityToOutboxDeadLetterEntityMapper();
        OutboxDeadLetterEntity outboxDeadLetterEntity = mapper.toEntity(outboxEventEntity);
        assertThat(outboxKafkaRepository.publishDeadLetter(outboxDeadLetterEntity, "outbox-dead-letter", "key")).isSameAs(outboxDeadLetterEntity);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("outbox-dead-letter"), eq("key"), value.capture());
        JsonNode envelope = jsonMapper.readTree(value.getValue());
        assertRequiredFields(envelope, "DeadLetterEnvelope");
        assertRequiredFields(envelope.path("originalEvent"), "EventEnvelope");
        assertThat(envelope.path("originalEvent").path("payload")).isEqualTo(outboxEventEntity.getPayload());
        assertThat(envelope.path("failureStage").asText()).isEqualTo("PUBLISHER");
        assertThat(envelope.path("failureReason").asText()).isEqualTo("invalid event");
        assertThat(envelope.path("attemptCount").asInt()).isEqualTo(3);
        assertThat(envelope.path("originalTopic").asText()).isEqualTo("events");
        outboxEventEntity.setLastError("{\"message\":\"no route\",\"originalTopic\":null}");
        assertThat(mapper.toEntity(outboxEventEntity).getOriginalTopic()).isNull();
        assertThat(mapper.toEntity(outboxEventEntity).getFailureReason()).isEqualTo("no route");
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void eventSpecificKeysAreScalarAndMissingKeysOrRoutesFail() {
        OutboxEventRoutingService routing = new OutboxEventRoutingService(new OutboxDeliverySettings(new MockEnvironment()
                .withProperty("OUTBOX_ROUTES", "{\"event\":{\"topic\":\"events\",\"keyField\":\"stoneId\"}}")));
        OutboxEventEntity outboxEventEntity = event();
        assertThat(routing.key(outboxEventEntity, routing.route(outboxEventEntity))).isEqualTo("19");
        outboxEventEntity.setPayload(jsonMapper.createObjectNode().put("other", 1));
        assertThatThrownBy(() -> routing.key(outboxEventEntity, routing.route(outboxEventEntity))).isInstanceOf(OutboxValidationException.class);
        outboxEventEntity.setEventType("unknown");
        assertThatThrownBy(() -> routing.route(outboxEventEntity)).isInstanceOf(OutboxValidationException.class);
    }

    private void assertRequiredFields(JsonNode envelope, String schemaName) throws Exception {
        JsonNode schema = jsonMapper.readTree(Files.readString(Path.of("../specs/0020-outbox-kafka-publication/asyncapi.json")))
                .path("components").path("schemas").path(schemaName);
        assertThat(envelope.size()).isEqualTo(schema.path("required").size());
        for (JsonNode required : schema.path("required")) assertThat(envelope.has(required.asText())).isTrue();
    }

    private OutboxEventEntity event() {
        OutboxEventEntity outboxEventEntity = new OutboxEventEntity();
        outboxEventEntity.setEventId(UUID.randomUUID());
        outboxEventEntity.setEventType("event");
        outboxEventEntity.setSchemaVersion(1);
        outboxEventEntity.setOccurredAt(Instant.parse("2026-10-12T00:00:00Z"));
        outboxEventEntity.setCreatedAt(outboxEventEntity.getOccurredAt());
        outboxEventEntity.setPayload(jsonMapper.createObjectNode().put("stoneId", 19).put("name", "Stone"));
        outboxEventEntity.setDeliveryState(OutboxDeliveryState.PENDING);
        return outboxEventEntity;
    }
}
