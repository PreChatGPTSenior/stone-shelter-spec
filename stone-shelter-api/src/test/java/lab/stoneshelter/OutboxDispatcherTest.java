package lab.stoneshelter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import lab.stoneshelter.entities.OutboxDeadLetterEntity;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.mappers.entities.OutboxEventEntityToOutboxDeadLetterEntityMapper;
import lab.stoneshelter.mappers.entities.OutboxWriteRequestToOutboxEventEntityMapper;
import lab.stoneshelter.repositories.OutboxEventRepository;
import lab.stoneshelter.repositories.OutboxKafkaRepository;
import lab.stoneshelter.services.OutboxDeliverySettings;
import lab.stoneshelter.services.OutboxDeliveryTransactionService;
import lab.stoneshelter.services.OutboxDispatcherService;
import lab.stoneshelter.services.OutboxEventRoutingService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.OutboxPayloadValidationService;
import lab.stoneshelter.services.OutboxService;
import lab.stoneshelter.shared.OutboxWriteRequest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {
    private static final Instant START = Instant.parse("2026-10-12T00:00:00Z");
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static final MutableClock CLOCK = new MutableClock();
    private static final MockEnvironment ENVIRONMENT = new MockEnvironment();
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static OutboxService outboxService;
    private static OutboxEventRepository outboxEventRepository;
    private static OutboxKafkaRepository outboxKafkaRepository;
    private static OutboxKafkaTopicService outboxKafkaTopicService;
    private static OutboxDispatcherService outboxDispatcherService;

    @BeforeAll
    static void setup() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        springLiquibase.afterPropertiesSet();
        jdbcTemplate = new JdbcTemplate(dataSource);
        outboxKafkaRepository = mock(OutboxKafkaRepository.class);
        outboxKafkaTopicService = mock(OutboxKafkaTopicService.class);
        context = new AnnotationConfigApplicationContext();
        context.setEnvironment(ENVIRONMENT);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
        context.registerBean(Clock.class, () -> CLOCK);
        context.registerBean(OutboxKafkaRepository.class, () -> outboxKafkaRepository);
        context.registerBean(OutboxKafkaTopicService.class, () -> outboxKafkaTopicService);
        context.register(Transactions.class, OutboxService.class, OutboxEventRepository.class,
                OutboxPayloadValidationService.class, OutboxWriteRequestToOutboxEventEntityMapper.class,
                OutboxDeliverySettings.class, OutboxEventRoutingService.class, OutboxDeliveryTransactionService.class,
                OutboxEventEntityToOutboxDeadLetterEntityMapper.class);
        // Keep scheduling out of this minimal context while retaining the transaction proxy.
        context.registerBean("dispatcher", Object.class, () -> new OutboxDispatcherService(
                context.getBean(OutboxDeliveryTransactionService.class), outboxKafkaRepository,
                context.getBean(OutboxEventRoutingService.class), context.getBean(OutboxEventEntityToOutboxDeadLetterEntityMapper.class),
                outboxKafkaTopicService));
        context.refresh();
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        outboxService = context.getBean(OutboxService.class);
        outboxEventRepository = context.getBean(OutboxEventRepository.class);
        outboxDispatcherService = context.getBean("dispatcher", OutboxDispatcherService.class);
    }

    @BeforeEach
    void resetOwnState() {
        CLOCK.setInstant(START);
        ENVIRONMENT.setProperty("OUTBOX_ROUTES", "{\"known\":{\"topic\":\"events\"},\"invalidKey\":{\"topic\":\"events\",\"keyField\":\"missing\"}}");
        ENVIRONMENT.setProperty("OUTBOX_BATCH_SIZE", "20");
        reset(outboxKafkaRepository, outboxKafkaTopicService);
        when(outboxKafkaRepository.isAvailable()).thenReturn(true);
        when(outboxKafkaTopicService.ensureReady()).thenReturn(true);
        when(outboxKafkaRepository.publish(any(), anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.getArgument(0);
        });
        when(outboxKafkaRepository.publishDeadLetter(any(), anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.getArgument(0);
        });
        outboxDispatcherService = restartedDispatcher();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS outbox_test_publication_recording");
        jdbcTemplate.update("DELETE FROM outbox_event");
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
        DATABASE.stop();
    }

    @Test
    void backendContextStartsPollingWithoutAnExplicitDispatchCall() throws Exception {
        UUID eventId = create("known");
        try (AnnotationConfigApplicationContext scheduledContext = new AnnotationConfigApplicationContext()) {
            scheduledContext.setParent(context);
            scheduledContext.setEnvironment(ENVIRONMENT);
            scheduledContext.register(OutboxDispatcherService.class);
            scheduledContext.refresh();
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(8).toNanos();
            while (find(eventId).getDeliveryState() == OutboxDeliveryState.PENDING && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.PUBLISHED);
        }
    }

    @Test
    void successfulPublicationFollowsAcknowledgementOutsideDatabaseTransactions() {
        UUID eventId = create("known");
        outboxDispatcherService.dispatch();
        OutboxEventEntity published = find(eventId);
        assertThat(published.getDeliveryState()).isEqualTo(OutboxDeliveryState.PUBLISHED);
        assertThat(published.getAttemptCount()).isEqualTo(1);
        assertThat(published.getPublishedAt()).isEqualTo(START);
        assertThat(published.getDeadLetteredAt()).isNull();
    }

    @Test
    void oneBadRouteDoesNotBlockOtherCommittedRowsAndRespectsBackoff() {
        UUID bad = create("unknown");
        UUID good = create("known");
        outboxDispatcherService.dispatch();
        assertThat(find(bad).getAttemptCount()).isEqualTo(1);
        assertThat(find(bad).getNextAttemptAt()).isEqualTo(START.plusSeconds(2));
        assertThat(find(good).getDeliveryState()).isEqualTo(OutboxDeliveryState.PUBLISHED);
        outboxDispatcherService.dispatch();
        assertThat(find(bad).getAttemptCount()).isEqualTo(1);
        CLOCK.setInstant(START.plusSeconds(2));
        outboxDispatcherService.dispatch();
        assertThat(find(bad).getAttemptCount()).isEqualTo(2);
        assertThat(find(bad).getNextAttemptAt()).isEqualTo(START.plusSeconds(6));
    }

    @Test
    void threeTotalFailuresAndRestartKeepDlqIntentUntilAck() {
        UUID eventId = create("invalidKey");
        outboxDispatcherService.dispatch();
        CLOCK.setInstant(START.plusSeconds(2));
        outboxDispatcherService = restartedDispatcher();
        outboxDispatcherService.dispatch();
        CLOCK.setInstant(START.plusSeconds(6));
        outboxDispatcherService.dispatch();
        assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.DEAD_LETTER_PENDING);
        assertThat(find(eventId).getAttemptCount()).isEqualTo(3);
        String originalError = find(eventId).getLastError();
        when(outboxKafkaRepository.publishDeadLetter(any(), anyString(), anyString())).thenThrow(new OutboxWriteException("transfer failed"));
        outboxDispatcherService.dispatch();
        assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.DEAD_LETTER_PENDING);
        assertThat(find(eventId).getLastError()).isEqualTo(originalError);
        assertThat(find(eventId).getNextAttemptAt()).isEqualTo(START.plusSeconds(8));
        ENVIRONMENT.setProperty("OUTBOX_ROUTES", "{\"invalidKey\":{\"topic\":\"changed-topic\"}}");
        doAnswer(invocation -> invocation.getArgument(0)).when(outboxKafkaRepository).publishDeadLetter(any(), anyString(), anyString());
        CLOCK.setInstant(START.plusSeconds(8));
        outboxDispatcherService = restartedDispatcher();
        outboxDispatcherService.dispatch();
        assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.DEAD_LETTERED);
        assertThat(find(eventId).getAttemptCount()).isEqualTo(3);
        assertThat(find(eventId).getPublishedAt()).isNull();
        assertThat(find(eventId).getDeadLetteredAt()).isEqualTo(START.plusSeconds(8));
        ArgumentCaptor<OutboxDeadLetterEntity> deadLetter = ArgumentCaptor.forClass(OutboxDeadLetterEntity.class);
        verify(outboxKafkaRepository, times(2)).publishDeadLetter(deadLetter.capture(), anyString(), anyString());
        assertThat(deadLetter.getValue().getOriginalTopic()).isEqualTo("events");
        verify(outboxKafkaRepository, never()).publish(any(), anyString(), anyString());
    }

    @Test
    void brokerOutageChargesNoAttemptsAndSuspendsSendingUntilRecovery() {
        UUID eventId = create("known");
        when(outboxKafkaRepository.publish(any(), anyString(), anyString())).thenThrow(new OutboxWriteException("broker unavailable"));
        when(outboxKafkaRepository.isAvailable()).thenReturn(false);
        outboxDispatcherService.dispatch();
        outboxDispatcherService.dispatch();
        assertThat(find(eventId).getAttemptCount()).isZero();
        assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.PENDING);
        verify(outboxKafkaRepository, times(1)).publish(any(), anyString(), anyString());
        when(outboxKafkaRepository.isAvailable()).thenReturn(true);
        doAnswer(invocation -> invocation.getArgument(0)).when(outboxKafkaRepository).publish(any(), anyString(), anyString());
        outboxDispatcherService.dispatch();
        assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.PUBLISHED);
    }

    @Test
    void databaseFailureAfterKafkaAckLeavesDurablePendingWorkWithoutChargingRetries() {
        UUID eventId = create("known");
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT outbox_test_publication_recording CHECK (published_at IS NULL)");
        outboxDispatcherService.dispatch();
        assertThat(find(eventId).getDeliveryState()).isEqualTo(OutboxDeliveryState.PENDING);
        assertThat(find(eventId).getAttemptCount()).isZero();
        assertThat(find(eventId).getPublishedAt()).isNull();
    }

    @Test
    void eligibilityIsBoundedByTheConfiguredBatchSize() {
        ENVIRONMENT.setProperty("OUTBOX_BATCH_SIZE", "1");
        UUID first = create("known");
        UUID second = create("known");
        outboxDispatcherService.dispatch();
        assertThat(java.util.List.of(find(first), find(second)).stream().filter(event -> event.getDeliveryState() == OutboxDeliveryState.PUBLISHED))
                .hasSize(1);
        outboxDispatcherService.dispatch();
        assertThat(find(first).getDeliveryState()).isEqualTo(OutboxDeliveryState.PUBLISHED);
        assertThat(find(second).getDeliveryState()).isEqualTo(OutboxDeliveryState.PUBLISHED);
    }

    private UUID create(String type) {
        UUID eventId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> outboxService.save(new OutboxWriteRequest(eventId, type, 1,
                JsonMapper.builder().build().createObjectNode().put("stoneId", 20))));
        return eventId;
    }

    private OutboxEventEntity find(UUID eventId) { return outboxEventRepository.findById(eventId).orElseThrow(); }

    private OutboxDispatcherService restartedDispatcher() {
        return new OutboxDispatcherService(context.getBean(OutboxDeliveryTransactionService.class), outboxKafkaRepository,
                context.getBean(OutboxEventRoutingService.class), context.getBean(OutboxEventEntityToOutboxDeadLetterEntityMapper.class), outboxKafkaTopicService);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }

    static class MutableClock extends Clock {
        private Instant instant;
        public void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
