package lab.stoneshelter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import lab.stoneshelter.entities.OutboxEventEntity;
import lab.stoneshelter.enums.OutboxDeliveryState;
import lab.stoneshelter.exceptions.OutboxIdentityConflictException;
import lab.stoneshelter.exceptions.OutboxValidationException;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.mappers.entities.OutboxWriteRequestToOutboxEventEntityMapper;
import lab.stoneshelter.repositories.OutboxEventRepository;
import lab.stoneshelter.services.OutboxPayloadValidationService;
import lab.stoneshelter.services.OutboxService;
import lab.stoneshelter.shared.OutboxWriteRequest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPersistenceTest {
    private static final Instant FIRST_WRITE = Instant.parse("2026-10-12T12:00:00Z");
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static final TestClock CLOCK = new TestClock();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static AnnotationConfigApplicationContext applicationContext;
    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static OutboxService outboxService;
    private static OutboxEventRepository outboxEventRepository;
    private final UUID eventId = UUID.randomUUID();

    @BeforeAll
    static void startIsolatedDatabaseAndTransactionContext() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        springLiquibase.afterPropertiesSet();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE outbox_test_business (marker TEXT PRIMARY KEY)");
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.registerBean(DataSource.class, () -> dataSource);
        applicationContext.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
        applicationContext.registerBean(Clock.class, () -> CLOCK);
        applicationContext.register(TransactionConfiguration.class, OutboxService.class, OutboxEventRepository.class,
                OutboxPayloadValidationService.class, OutboxWriteRequestToOutboxEventEntityMapper.class);
        applicationContext.refresh();
        transactionTemplate = new TransactionTemplate(applicationContext.getBean(PlatformTransactionManager.class));
        outboxService = applicationContext.getBean(OutboxService.class);
        outboxEventRepository = applicationContext.getBean(OutboxEventRepository.class);
    }

    @BeforeEach
    void resetClock() { CLOCK.setInstant(FIRST_WRITE); }

    @AfterEach
    void removeOwnFixtures() {
        jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS outbox_test_write_failure");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM outbox_test_business");
    }

    @AfterAll
    static void stopOwnedResources() {
        if (applicationContext != null) applicationContext.close();
        DATABASE.stop();
    }

    @Test
    void rejectsAWriteWithoutTheCallerTransaction() {
        assertThatThrownBy(() -> outboxService.save(request(eventId, "event", 1, payload("first"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(eventCount()).isZero();
    }

    @Test
    void commitsBusinessAndIndividualEventsTogetherWithOutboxMetadata() {
        OutboxEventEntity outboxEventEntity = transactionTemplate.execute(status -> {
            jdbcTemplate.update("INSERT INTO outbox_test_business VALUES (?)", "created");
            OutboxEventEntity firstOutboxEventEntity = outboxService.save(request(eventId, "event", 1, payload("first")));
            outboxService.save(request(UUID.randomUUID(), "another.event", 3, payload("second")));
            return firstOutboxEventEntity;
        });
        assertThat(outboxEventEntity.getEventId()).isEqualTo(eventId);
        assertThat(outboxEventEntity.getPayload()).isEqualTo(payload("first"));
        assertThat(outboxEventEntity.getOccurredAt()).isEqualTo(FIRST_WRITE);
        assertThat(outboxEventEntity.getCreatedAt()).isEqualTo(FIRST_WRITE);
        assertThat(outboxEventEntity.getDeliveryState()).isEqualTo(OutboxDeliveryState.PENDING);
        assertThat(eventCount()).isEqualTo(2);
        assertThat(businessCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_name = 'outbox_event' AND column_name = 'payload'", String.class)).isEqualTo("jsonb");
        jdbcTemplate.update("DELETE FROM outbox_test_business");
        assertThat(eventCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_name = 'outbox_event' AND constraint_type = 'FOREIGN KEY'", Integer.class)).isZero();
    }

    @Test
    void callerRollbackRetainsNeitherBusinessDataNorEvents() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO outbox_test_business VALUES (?)", "rolled-back");
            outboxService.save(request(eventId, "event", 1, payload("first")));
            status.setRollbackOnly();
        });
        assertThat(eventCount()).isZero();
        assertThat(businessCount()).isZero();
    }

    @Test
    void caughtValidationFailureStillPreventsTheBusinessCommit() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO outbox_test_business VALUES (?)", "invalid");
            try {
                outboxService.save(request(eventId, "event", 1, JSON.createObjectNode()));
            } catch (OutboxValidationException expected) {
                // Even a caller swallowing the error must not commit partial business work.
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(eventCount()).isZero();
        assertThat(businessCount()).isZero();
    }

    @Test
    void databaseWriteFailureRollsBackBusinessWork() {
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT outbox_test_write_failure CHECK (event_type <> 'rejected')");
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO outbox_test_business VALUES (?)", "failed-write");
            outboxService.save(request(eventId, "rejected", 1, payload("first")));
        })).isInstanceOf(OutboxWriteException.class).hasCauseInstanceOf(DataIntegrityViolationException.class);
        assertThat(eventCount()).isZero();
        assertThat(businessCount()).isZero();
    }

    @Test
    void validDuplicateIgnoresPayloadAndPreservesOriginalTimesAndState() {
        OutboxEventEntity firstOutboxEventEntity = transactionTemplate.execute(status ->
                outboxService.save(request(eventId, "event", 1, payload("first"))));
        CLOCK.setInstant(FIRST_WRITE.plusSeconds(60));
        OutboxEventEntity repeatedOutboxEventEntity = transactionTemplate.execute(status ->
                outboxService.save(request(eventId, "event", 1, payload("changed"))));
        assertThat(eventCount()).isEqualTo(1);
        assertThat(repeatedOutboxEventEntity.getPayload()).isEqualTo(firstOutboxEventEntity.getPayload());
        assertThat(repeatedOutboxEventEntity.getOccurredAt()).isEqualTo(firstOutboxEventEntity.getOccurredAt());
        assertThat(repeatedOutboxEventEntity.getCreatedAt()).isEqualTo(firstOutboxEventEntity.getCreatedAt());
        assertThat(repeatedOutboxEventEntity.getDeliveryState()).isEqualTo(firstOutboxEventEntity.getDeliveryState());
    }

    @Test
    void mismatchedTypeOrVersionRollsBackOnlyTheNewCallerWork() {
        transactionTemplate.executeWithoutResult(status -> outboxService.save(request(eventId, "event", 1, payload("first"))));
        for (OutboxWriteRequest mismatch : new OutboxWriteRequest[] {
                request(eventId, "different", 1, payload("changed")), request(eventId, "event", 2, payload("changed")) }) {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO outbox_test_business VALUES (?)", "mismatch");
                outboxService.save(mismatch);
            })).isInstanceOf(OutboxIdentityConflictException.class);
            assertThat(businessCount()).isZero();
        }
        assertThat(eventCount()).isEqualTo(1);
        assertThat(outboxEventRepository.findById(eventId).orElseThrow().getPayload()).isEqualTo(payload("first"));
    }

    @Test
    void validatesRepeatedPayloadBeforeLookingUpTheExistingIdentity() {
        transactionTemplate.executeWithoutResult(status -> outboxService.save(request(eventId, "event", 1, payload("first"))));
        for (JsonNode invalid : new JsonNode[] { JSON.createObjectNode(), JSON.createArrayNode(),
                JSON.createObjectNode().put("value", "a".repeat(262144)) }) {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                    outboxService.save(request(eventId, "event", 1, invalid))))
                    .isInstanceOf(OutboxValidationException.class);
        }
        assertThat(eventCount()).isEqualTo(1);
        assertThat(outboxEventRepository.findById(eventId).orElseThrow().getPayload()).isEqualTo(payload("first"));
    }

    @Test
    void concurrentMatchingIdentitiesReturnTheSameSinglePersistedEvent() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<OutboxEventEntity> first = executorService.submit(() -> concurrentSave("event", "first", ready, start));
            Future<OutboxEventEntity> second = executorService.submit(() -> concurrentSave("event", "second", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            OutboxEventEntity firstOutboxEventEntity = first.get(10, TimeUnit.SECONDS);
            OutboxEventEntity secondOutboxEventEntity = second.get(10, TimeUnit.SECONDS);
            assertThat(firstOutboxEventEntity.getPayload()).isEqualTo(secondOutboxEventEntity.getPayload());
            assertThat(eventCount()).isEqualTo(1);
            assertThat(businessCount()).isEqualTo(2);
        } finally {
            start.countDown();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentConflictingIdentitiesAllowOnlyOneBusinessCommit() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executorService.submit(() -> conflictingSave("first.type", ready, start));
            Future<String> second = executorService.submit(() -> conflictingSave("second.type", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new String[] { first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS) })
                    .containsOnlyOnce("conflict");
            assertThat(eventCount()).isEqualTo(1);
            assertThat(businessCount()).isEqualTo(1);
            assertThat(outboxEventRepository.findById(eventId).orElseThrow().getEventType())
                    .isEqualTo(jdbcTemplate.queryForObject("SELECT marker FROM outbox_test_business", String.class));
        } finally {
            start.countDown();
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void migrationRejectsInvalidPayloadsAndNonpositiveSchemaVersions() {
        for (String invalid : new String[] { "{}", "[]", "true", "null" }) {
            assertThatThrownBy(() -> jdbcTemplate.update("""
                    INSERT INTO outbox_event VALUES (?, 'event', 1, now(), ?::jsonb, now(), 'PENDING')
                    """, UUID.randomUUID(), invalid)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO outbox_event VALUES (?, 'event', 0, now(), '{"value":1}'::jsonb, now(), 'PENDING')
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(eventCount()).isZero();
    }

    private OutboxEventEntity concurrentSave(String type, String marker, CountDownLatch ready, CountDownLatch start) {
        return transactionTemplate.execute(status -> {
            ready.countDown();
            await(start);
            jdbcTemplate.update("INSERT INTO outbox_test_business VALUES (?)", marker);
            return outboxService.save(request(eventId, type, 1, payload(marker)));
        });
    }

    private String conflictingSave(String type, CountDownLatch ready, CountDownLatch start) {
        try {
            concurrentSave(type, type, ready, start);
            return type;
        } catch (OutboxIdentityConflictException expected) {
            return "conflict";
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent fixture did not start.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent fixture was interrupted.", exception);
        }
    }

    private JsonNode payload(String value) { return JSON.createObjectNode().put("value", value).put("stoneId", 19); }
    private OutboxWriteRequest request(UUID id, String type, int version, JsonNode payload) {
        return new OutboxWriteRequest(id, type, version, payload);
    }
    private int eventCount() { return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class); }
    private int businessCount() { return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_test_business", Integer.class); }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    static class TestClock extends Clock {
        private Instant instant;
        public void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
