package lab.stoneshelter;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneIndexProcessingPersistenceTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static StoneIndexProcessingTransactionService service;
    private static StoneCardIndexStateRepository stateRepository;
    private static StoneCardIndexAttemptRepository attemptRepository;

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
        context.register(Transactions.class, StoneIndexProcessingTransactionService.class,
                StoneCardEmbeddingRepository.class, StoneCardIndexStateRepository.class, StoneCardIndexAttemptRepository.class);
        context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        service = context.getBean(StoneIndexProcessingTransactionService.class);
        stateRepository = context.getBean(StoneCardIndexStateRepository.class);
        attemptRepository = context.getBean(StoneCardIndexAttemptRepository.class);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("ALTER TABLE stone_card_index_state DROP CONSTRAINT IF EXISTS test_reject_version");
        jdbcTemplate.update("DELETE FROM stone_card_embedding");
        jdbcTemplate.update("DELETE FROM stone_card_index_state");
        jdbcTemplate.update("DELETE FROM stone_card_index_attempt");
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); DATABASE.stop(); }

    @Test
    void vectorAndVersionCommitTogetherAndStalePreparedWorkIsDiscarded() {
        assertThat(service.apply(prepared(10, "one", "profile"), 1)).isTrue();
        assertThat(service.apply(prepared(10, "three", "profile"), 3)).isTrue();
        assertThat(service.apply(prepared(10, "two", "profile"), 2)).isFalse();
        assertThat(service.isStale(10, 3)).isTrue();
        assertThat(stateRepository.findById(10).orElseThrow()).isEqualTo(new StoneCardIndexStateEntity(10, 3, false));
        assertThat(jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 10", String.class)).isEqualTo("three");
    }

    @Test
    void deletionPersistsTombstoneAndPreventsDelayedResurrection() {
        assertThat(service.apply(prepared(20, "before", "profile"), 1)).isTrue();
        assertThat(service.delete(20, 2)).isTrue();
        assertThat(service.apply(prepared(20, "delayed", "profile"), 1)).isFalse();
        assertThat(new StoneCardIndexStateRepository(jdbcTemplate).findById(20).orElseThrow())
                .isEqualTo(new StoneCardIndexStateEntity(20, 2, true));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding WHERE stone_id = 20", Long.class)).isZero();
    }

    @Test
    void stateFailureRollsBackBothVectorUpdateAndDeletion() {
        assertThat(service.apply(prepared(30, "original", "profile"), 1)).isTrue();
        jdbcTemplate.execute("ALTER TABLE stone_card_index_state ADD CONSTRAINT test_reject_version CHECK (source_version <= 1)");
        assertThatThrownBy(() -> service.apply(prepared(30, "rejected", "profile"), 2)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> service.delete(30, 2)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 30", String.class)).isEqualTo("original");
        assertThat(stateRepository.findById(30).orElseThrow().sourceVersion()).isEqualTo(1);
    }

    @Test
    void concurrentApplyLocksAndRechecksTheVersionAndRejectsProfileChanges() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> second = () -> service.apply(prepared(40, "second", "profile"), 2);
            Callable<Boolean> third = () -> service.apply(prepared(40, "third", "profile"), 3);
            for (var result : executor.invokeAll(List.of(second, third))) result.get();
        }
        assertThat(stateRepository.findById(40).orElseThrow().sourceVersion()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 40", String.class)).isEqualTo("third");
        assertThatThrownBy(() -> service.apply(prepared(50, "incompatible", "other-profile"), 1))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        assertThat(stateRepository.findById(50)).isEmpty();
    }

    @Test
    void attemptsAndRawBytesRemainVisibleAfterRepositoryRestart() {
        StoneCardIndexAttemptEntity stoneCardIndexAttemptEntity = new StoneCardIndexAttemptEntity();
        stoneCardIndexAttemptEntity.setSourceTopic("stone-card-index-events");
        stoneCardIndexAttemptEntity.setSourceOffset(100);
        stoneCardIndexAttemptEntity.setAttemptCount(3);
        stoneCardIndexAttemptEntity.setDeadLetterPending(true);
        stoneCardIndexAttemptEntity.setOriginalRecord(new byte[] {0, (byte) 255, 13});
        stoneCardIndexAttemptEntity.setLastError("invalid");
        stoneCardIndexAttemptEntity.setNextAttemptAt(Instant.parse("2026-10-12T00:00:00Z"));
        assertThat(attemptRepository.save(stoneCardIndexAttemptEntity).getAttemptCount()).isEqualTo(3);
        StoneCardIndexAttemptEntity restarted = new StoneCardIndexAttemptRepository(jdbcTemplate).findById("stone-card-index-events", 0, 100).orElseThrow();
        assertThat(restarted.getOriginalRecord()).containsExactly(0, (byte) 255, 13);
        assertThat(restarted.getDeadLetterPending()).isTrue();
        assertThat(restarted.getNextAttemptAt()).isEqualTo(stoneCardIndexAttemptEntity.getNextAttemptAt());
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
