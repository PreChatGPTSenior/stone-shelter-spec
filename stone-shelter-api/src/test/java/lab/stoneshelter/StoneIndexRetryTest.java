package lab.stoneshelter;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.services.StoneCardEmbeddingService;
import lab.stoneshelter.services.StoneCardEmbeddingTransactionService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.services.StoneIndexProcessingService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import lab.stoneshelter.services.StoneIndexAttemptTransactionService;
import lab.stoneshelter.repositories.OutboxKafkaRepository;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import lab.stoneshelter.enums.ModelAdmissionMode;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import javax.sql.DataSource;
import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.entities.StoneCardIndexAttemptEntity;
import lab.stoneshelter.entities.StoneCardIndexStateEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.repositories.StoneCardIndexAttemptRepository;
import lab.stoneshelter.repositories.StoneCardIndexStateRepository;
import lab.stoneshelter.services.StoneIndexProcessingTransactionService;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;

class StoneIndexRetryTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static StoneIndexProcessingTransactionService service;
    private static StoneCardIndexStateRepository stateRepository;
    private static StoneCardIndexAttemptRepository attemptRepository;
    private static StoneDocumentEmbeddingService embeddingService;
    private static StoneIndexProcessingService processingService;
    private static final Instant START = Instant.parse("2026-10-12T00:00:00Z");
    private static final MutableClock CLOCK = new MutableClock();
    private static OutboxKafkaRepository kafkaRepository;

    @BeforeAll
    static void setup() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        springLiquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        embeddingService = mock(StoneDocumentEmbeddingService.class);
        kafkaRepository = mock(OutboxKafkaRepository.class);
        context.registerBean(OutboxKafkaRepository.class, () -> kafkaRepository);
        context.register(StoneIndexAttemptTransactionService.class);
        context.registerBean(Clock.class, () -> CLOCK);
        context.registerBean(StoneDocumentEmbeddingService.class, () -> embeddingService);
        context.registerBean(ModelInvocationAdmissionService.class, () -> mock(ModelInvocationAdmissionService.class));
        context.registerBean(StoneDocumentIndexSettings.class, () -> new StoneDocumentIndexSettings(new MockEnvironment()));
        context.register(StoneCardEmbeddingService.class, StoneCardEmbeddingTransactionService.class,
                StoneCardSaveRequestToStoneCardEmbeddingEntityMapper.class, StoneCardEmbeddingEntityToStoneCardSaveResponseMapper.class,
                StoneIndexProcessingService.class);
        context.register(Transactions.class, StoneIndexProcessingTransactionService.class,
                StoneCardEmbeddingRepository.class, StoneCardIndexStateRepository.class, StoneCardIndexAttemptRepository.class);
        context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        service = context.getBean(StoneIndexProcessingTransactionService.class);
        stateRepository = context.getBean(StoneCardIndexStateRepository.class);
        attemptRepository = context.getBean(StoneCardIndexAttemptRepository.class);
        processingService = context.getBean(StoneIndexProcessingService.class);
    }

    @AfterEach
    void cleanup() {
        reset(embeddingService, kafkaRepository);
        jdbcTemplate.execute("ALTER TABLE stone_card_index_state DROP CONSTRAINT IF EXISTS test_reject_version");
        jdbcTemplate.update("DELETE FROM stone_card_embedding");
        jdbcTemplate.update("DELETE FROM stone_card_index_state");
        jdbcTemplate.update("DELETE FROM stone_card_index_attempt");
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); DATABASE.stop(); }

    @BeforeEach
    void resetProviders() {
        CLOCK.instant = START;
        when(kafkaRepository.isAvailable()).thenReturn(true);
        when(kafkaRepository.publishDeadLetter(any(), anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.getArgument(0);
        });
    }

    @Test
    void threeTotalFailuresPersistAcrossRestartAndPauseTheSourcePartition() {
        when(embeddingService.embed(any())).thenThrow(new IllegalStateException("event processing failure"));
        var record = new ConsumerRecord<>("stone-card-index-events", 0, 100, "10", event(10, 1, false));
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        consumer().receive(record, acknowledgment);
        verify(acknowledgment).nack(java.time.Duration.ofSeconds(2));
        assertThat(attemptRepository.findById(record.topic(), 0, 100).orElseThrow().getAttemptCount()).isEqualTo(1);
        consumer().receive(record, acknowledgment);
        verify(embeddingService, times(1)).embed(any());
        CLOCK.instant = START.plusSeconds(2);
        consumer().receive(record, acknowledgment);
        assertThat(attemptRepository.findById(record.topic(), 0, 100).orElseThrow().getAttemptCount()).isEqualTo(2);
        verify(acknowledgment).nack(java.time.Duration.ofSeconds(4));
        CLOCK.instant = START.plusSeconds(6);
        when(kafkaRepository.publishDeadLetter(any(), anyString(), anyString())).thenThrow(new OutboxWriteException("broker down"));
        consumer().receive(record, acknowledgment);
        verify(acknowledgment, never()).acknowledge();
        var failed = new StoneCardIndexAttemptRepository(jdbcTemplate).findById(record.topic(), 0, 100).orElseThrow();
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getDeadLetterPending()).isTrue();
        assertThat(failed.getOriginalRecord()).isEqualTo(record.value());
        doAnswer(invocation -> invocation.getArgument(0)).when(kafkaRepository).publishDeadLetter(any(), anyString(), anyString());
        CLOCK.instant = START.plusSeconds(8);
        consumer().receive(record, acknowledgment);
        verify(acknowledgment).acknowledge();
        verify(embeddingService, times(3)).embed(any());
        ArgumentCaptor<OutboxDeadLetterEntity> deadLetter = ArgumentCaptor.forClass(OutboxDeadLetterEntity.class);
        verify(kafkaRepository, times(2)).publishDeadLetter(deadLetter.capture(), anyString(), anyString());
        assertThat(deadLetter.getValue().getAttemptCount()).isEqualTo(3);
        assertThat(deadLetter.getValue().getFailureStage()).isEqualTo("CONSUMER");
        assertThat(deadLetter.getValue().getOriginalTopic()).isEqualTo(record.topic());
    }

    @Test
    void dependencyOutagesAndIncompatibleProfilesWaitWithoutAttemptsOrAck() {
        var record = new ConsumerRecord<>("stone-card-index-events", 0, 200, "20", event(20, 1, false));
        when(embeddingService.embed(any())).thenThrow(new StoneDocumentDependencyUnavailableException("Ollama unavailable"));
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        consumer().receive(record, acknowledgment);
        assertThat(attemptRepository.findById(record.topic(), 0, 200)).isEmpty();
        when(kafkaRepository.isAvailable()).thenReturn(false);
        doThrow(new IllegalStateException("failure while Kafka down")).when(embeddingService).embed(any());
        consumer().receive(record, acknowledgment);
        assertThat(attemptRepository.findById(record.topic(), 0, 200)).isEmpty();
        verify(acknowledgment, never()).acknowledge();
        verify(kafkaRepository, never()).publishDeadLetter(any(), anyString(), anyString());
        when(kafkaRepository.isAvailable()).thenReturn(true);
        doReturn(new StoneDocumentEmbeddingEntity("model", "other-profile", prepared(20, "", "other-profile").getEmbedding(), 12)).when(embeddingService).embed(any());
        assertThat(service.apply(prepared(30, "existing", "profile"), 1)).isTrue();
        consumer().receive(record, acknowledgment);
        assertThat(attemptRepository.findById(record.topic(), 0, 200)).isEmpty();
        assertThat(stateRepository.findById(20)).isEmpty();
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void malformedBytesGoDirectlyToDlqAndRecoveryDoesNotNeedTheExpiredSourceRecord() {
        byte[] bytes = new byte[] {(byte) 255, 0, 13, (byte) 128};
        var record = new ConsumerRecord<>("stone-card-index-events", 0, 300, "invalid", bytes);
        when(kafkaRepository.publishDeadLetter(any(), anyString(), anyString())).thenThrow(new OutboxWriteException("DLQ unavailable"));
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        consumer().receive(record, acknowledgment);
        verify(acknowledgment, never()).acknowledge();
        var pending = attemptRepository.findById(record.topic(), 0, 300).orElseThrow();
        assertThat(pending.getAttemptCount()).isZero();
        assertThat(pending.getOriginalRecord()).isEqualTo(bytes);
        CLOCK.instant = START.plusSeconds(2);
        doAnswer(invocation -> invocation.getArgument(0)).when(kafkaRepository).publishDeadLetter(any(), anyString(), anyString());
        processingService.recoverPendingDeadLetters();
        ArgumentCaptor<OutboxDeadLetterEntity> deadLetter = ArgumentCaptor.forClass(OutboxDeadLetterEntity.class);
        verify(kafkaRepository, times(2)).publishDeadLetter(deadLetter.capture(), anyString(), anyString());
        assertThat(deadLetter.getValue().getOriginalEvent().get("encoding").asString()).isEqualTo("base64");
        assertThat(java.util.Base64.getDecoder().decode(deadLetter.getValue().getOriginalEvent().get("value").asString())).isEqualTo(bytes);
        assertThat(attemptRepository.findById(record.topic(), 0, 300).orElseThrow().getDeadLettered()).isTrue();
        consumer().receive(record, acknowledgment);
        verify(acknowledgment).acknowledge();
        verifyNoInteractions(embeddingService);
    }

    @Test
    void unknownTypesAndUnsupportedSchemasSkipRetriesAndStorageFailuresWait() {
        byte[] valid = event(40, 1, false);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        for (int i = 0; i < 2; i++) {
            var event = (tools.jackson.databind.node.ObjectNode) jsonMapper.readTree(valid);
            if (i == 0) event.put("eventType", "UNKNOWN"); else event.put("schemaVersion", 2);
            assertThat(processingService.processRecord("stone-card-index-events", 0, 400 + i,
                    jsonMapper.writeValueAsBytes(event), "40").completed()).isTrue();
            assertThat(attemptRepository.findById("stone-card-index-events", 0, 400 + i).orElseThrow().getAttemptCount()).isZero();
        }
        verifyNoInteractions(embeddingService);
        jdbcTemplate.execute("ALTER TABLE stone_card_index_state ADD CONSTRAINT test_reject_version CHECK (source_version = 0)");
        when(embeddingService.embed(any())).thenReturn(new StoneDocumentEmbeddingEntity("model", "profile", prepared(40, "", "profile").getEmbedding(), 12));
        assertThat(processingService.processRecord("stone-card-index-events", 0, 402, valid, "40").completed()).isFalse();
        assertThat(attemptRepository.findById("stone-card-index-events", 0, 402)).isEmpty();
        assertThat(stateRepository.findById(40)).isEmpty();
    }

    @Test
    void healthyAdmissionWaitDoesNotChargeAttemptsAcknowledgeOrEnterDlq() {
        when(embeddingService.embed(any())).thenAnswer(call -> {
            lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria criteria = call.getArgument(0);
            assertThat(criteria.admissionMode()).isEqualTo(ModelAdmissionMode.BACKGROUND);
            throw new ModelAdmissionDeferredException(START.plusSeconds(2));
        });
        var record = new ConsumerRecord<>("stone-card-index-events", 0, 500, "50", event(50, 1, false));
        Acknowledgment acknowledgment = mock(Acknowledgment.class); consumer().receive(record, acknowledgment);
        verify(acknowledgment).nack(java.time.Duration.ofSeconds(2)); verify(acknowledgment, never()).acknowledge();
        assertThat(attemptRepository.findById(record.topic(), 0, 500)).isEmpty();
        verify(kafkaRepository, never()).publishDeadLetter(any(), anyString(), anyString());
    }

    private StoneIndexConsumerService consumer() {
        ConsumerFactory<?, ?> consumerFactory = mock(ConsumerFactory.class);
        when(consumerFactory.getConfigurationProperties()).thenReturn(java.util.Map.of("bootstrap.servers", "localhost:29092"));
        StoneIndexProcessingService restartedProcessingService = new StoneIndexProcessingService(service,
                context.getBean(StoneCardEmbeddingService.class), CLOCK);
        restartedProcessingService.configureRetryServices(new StoneCardIndexAttemptRepository(jdbcTemplate),
                context.getBeanProvider(StoneIndexAttemptTransactionService.class), context.getBeanProvider(OutboxKafkaRepository.class));
        return new StoneIndexConsumerService(consumerFactory, restartedProcessingService, CLOCK);
    }

    private byte[] event(long id, long version, boolean deleted) {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        var payload = jsonMapper.createObjectNode().put("stoneId", id).put("sourceVersion", version);
        if (!deleted) payload.put("name", "Luna").put("stoneType", "BASALT").put("stoneSize", "SMALL").putNull("biography");
        return jsonMapper.writeValueAsBytes(jsonMapper.createObjectNode().put("eventId", UUID.randomUUID().toString())
                .put("eventType", deleted ? "STONE_CARD_INDEX_DELETED" : "STONE_CARD_INDEX_UPDATED")
                .put("schemaVersion", 1).put("occurredAt", CLOCK.instant().toString()).set("payload", payload));
    }

    private StoneCardEmbeddingEntity prepared(long id, String text, String profile) {
        StoneCardEmbeddingEntity stoneCardEmbeddingEntity = new StoneCardEmbeddingEntity();
        stoneCardEmbeddingEntity.setStoneId(id);
        stoneCardEmbeddingEntity.setText(text);
        stoneCardEmbeddingEntity.setEmbeddingProfile(profile);
        double[] vector = new double[1024];
        vector[0] = 1;
        stoneCardEmbeddingEntity.setEmbedding(vector);
        return stoneCardEmbeddingEntity;
    }

    static class MutableClock extends Clock {
        private Instant instant = START;
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
