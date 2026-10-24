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
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
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
import static org.mockito.ArgumentMatchers.any;

class StoneIndexProcessingTest {
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
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);

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
        context.registerBean(Clock.class, () -> CLOCK);
        context.registerBean(StoneDocumentEmbeddingService.class, () -> embeddingService);
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
        reset(embeddingService);
        jdbcTemplate.execute("ALTER TABLE stone_card_index_state DROP CONSTRAINT IF EXISTS test_reject_version");
        jdbcTemplate.update("DELETE FROM stone_card_embedding");
        jdbcTemplate.update("DELETE FROM stone_card_index_state");
        jdbcTemplate.update("DELETE FROM stone_card_index_attempt");
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); DATABASE.stop(); }

    @Test
    void staleAndDuplicateEventsSkipBeforeOllamaAndAcknowledgeCommittedState() {
        assertThat(service.delete(10, 3)).isTrue();
        assertThat(processingService.process(event(10, 2, false), "10").completed()).isTrue();
        assertThat(processingService.process(event(10, 3, true), "10").completed()).isTrue();
        verifyNoInteractions(embeddingService);
        assertThat(stateRepository.findById(10).orElseThrow().deleted()).isTrue();
    }

    @Test
    void preparationHasNoTransactionAndProjectionCommitsBeforeManualAcknowledgement() {
        when(embeddingService.embed(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding", Long.class)).isZero();
            return new StoneDocumentEmbeddingEntity("model", "profile", prepared(20, "", "profile").getEmbedding(), 12);
        });
        ConsumerFactory<?, ?> consumerFactory = mock(ConsumerFactory.class);
        when(consumerFactory.getConfigurationProperties()).thenReturn(java.util.Map.of("bootstrap.servers", "localhost:29092"));
        StoneIndexConsumerService consumer = new StoneIndexConsumerService(consumerFactory, processingService, CLOCK);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        consumer.receive(new ConsumerRecord<>("stone-card-index-events", 0, 1, "20", event(20, 1, false)), acknowledgment);
        verify(acknowledgment).acknowledge();
        assertThat(stateRepository.findById(20).orElseThrow().sourceVersion()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 20", String.class))
                .contains("Name: Luna", "Stone type: BASALT", "Size: SMALL");
        consumer.stop();
    }

    @Test
    void versionIsRecheckedAfterEmbeddingPreparation() {
        when(embeddingService.embed(any())).thenAnswer(invocation -> {
            assertThat(service.delete(30, 3)).isTrue();
            return new StoneDocumentEmbeddingEntity("model", "profile", prepared(30, "", "profile").getEmbedding(), 12);
        });
        assertThat(processingService.process(event(30, 2, false), "30").completed()).isTrue();
        assertThat(stateRepository.findById(30).orElseThrow()).isEqualTo(new StoneCardIndexStateEntity(30, 3, true));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding WHERE stone_id = 30", Long.class)).isZero();
    }

    @Test
    void consumerSettingsUseManualCommitEarliestAndLosslessBytes() {
        ConsumerFactory<?, ?> consumerFactory = mock(ConsumerFactory.class);
        when(consumerFactory.getConfigurationProperties()).thenReturn(java.util.Map.of("bootstrap.servers", "localhost:29092"));
        StoneIndexConsumerService consumer = new StoneIndexConsumerService(consumerFactory, processingService, CLOCK);
        assertThat(consumer.consumerConfiguration(java.util.Map.of())).containsEntry("enable.auto.commit", false)
                .containsEntry("auto.offset.reset", "earliest").containsEntry("group.id", "stone-card-indexer")
                .containsEntry("value.deserializer", org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        consumer.stop();
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

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
