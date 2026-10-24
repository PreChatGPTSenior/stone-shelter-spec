package lab.stoneshelter;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import lab.stoneshelter.entities.StoneCardReindexItemEntity;
import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.enums.StoneCardReindexStatus;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.mappers.dtos.StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper;
import lab.stoneshelter.repositories.StoneCardReindexControlRepository;
import lab.stoneshelter.repositories.StoneCardReindexItemRepository;
import lab.stoneshelter.repositories.StoneCardReindexJobRepository;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneCardReindexPersistenceTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static JdbcTemplate jdbcTemplate;
    private static StoneCardReindexJobRepository jobRepository;
    private static StoneCardReindexItemRepository itemRepository;
    private static StoneCardReindexControlRepository controlRepository;
    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setup() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        springLiquibase.afterPropertiesSet();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jobRepository = new StoneCardReindexJobRepository(jdbcTemplate);
        itemRepository = new StoneCardReindexItemRepository(jdbcTemplate);
        controlRepository = new StoneCardReindexControlRepository(jdbcTemplate);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("UPDATE stone_card_reindex_control SET active_job = NULL, creation_blocked = FALSE");
        jdbcTemplate.update("DELETE FROM stone_card_reindex_item");
        jdbcTemplate.update("DELETE FROM stone_card_reindex_job");
    }

    @AfterAll
    static void stop() { DATABASE.stop(); }

    @Test
    void databaseConstraintPreventsTwoActiveJobsAndGateIsDurable() {
        StoneCardReindexJobEntity job = job(StoneCardReindexTarget.ALL);
        assertThat(transactionTemplate.execute(status -> controlRepository.activate(jobRepository.save(job))).getJobId()).isEqualTo(job.getJobId());
        assertThatThrownBy(() -> jobRepository.save(job(StoneCardReindexTarget.SELECTED))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(new StoneCardReindexControlRepository(jdbcTemplate).findActiveJobId()).contains(job.getJobId());
        assertThat(controlRepository.isCreationBlockedForShare()).isTrue();
    }

    @Test
    void manifestUniquenessAndDerivedProgressSurviveRestartAndMapToHttpDtos() {
        StoneCardReindexJobEntity job = jobRepository.save(job(StoneCardReindexTarget.ALL));
        assertThat(itemRepository.save(item(job.getJobId(), 10, "CARD", "ENQUEUED", null)).getOutcome()).isEqualTo("ENQUEUED");
        assertThat(itemRepository.save(item(job.getJobId(), 10, "CARD", "ENQUEUED", null)).getOutcome()).isEqualTo("ENQUEUED");
        assertThat(itemRepository.save(item(job.getJobId(), 20, "CARD", "FAILED", "Invalid snapshot")).getOutcome()).isEqualTo("FAILED");
        assertThat(itemRepository.save(item(job.getJobId(), 30, "ORPHAN", "RECONCILED", null)).getOutcome()).isEqualTo("RECONCILED");
        StoneCardReindexJobEntity restored = new StoneCardReindexJobRepository(jdbcTemplate).findById(job.getJobId()).orElseThrow();
        var response = new StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper().toDto(restored);
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getProcessedCount()).isEqualTo(3);
        assertThat(response.getEnqueuedCount()).isEqualTo(2);
        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getReconciledCount()).isEqualTo(1);
        assertThat(response.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.stoneId()).isEqualTo(20);
            assertThat(error.error()).isEqualTo("Invalid snapshot");
        });
        assertThat(new StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper().toDto(restored).status()).isEqualTo(StoneCardReindexStatus.PENDING);
    }

    @Test
    void jobManifestAndCreationControlRollbackTogether() {
        StoneCardReindexJobEntity job = job(StoneCardReindexTarget.ALL);
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(controlRepository.findActiveJobIdForUpdate()).isEmpty();
            assertThat(controlRepository.activate(jobRepository.save(job)).getJobId()).isEqualTo(job.getJobId());
            assertThat(itemRepository.save(item(job.getJobId(), 40, "CARD", "PENDING", null)).getStoneId()).isEqualTo(40);
            status.setRollbackOnly();
        });
        assertThat(jobRepository.findById(job.getJobId())).isEmpty();
        assertThat(controlRepository.findActiveJobId()).isEmpty();
        assertThat(controlRepository.isCreationBlockedForShare()).isFalse();
    }

    @Test
    void enqueuedJobReleasesGateAtomicallyAndTargetedJobsKeepCreationOpen() {
        StoneCardReindexJobEntity job = transactionTemplate.execute(status -> controlRepository.activate(jobRepository.save(job(StoneCardReindexTarget.ALL))));
        transactionTemplate.executeWithoutResult(status -> {
            job.setStatus(StoneCardReindexStatus.ENQUEUED);
            assertThat(jobRepository.save(job).getStatus()).isEqualTo(StoneCardReindexStatus.ENQUEUED);
            controlRepository.release(job.getJobId());
        });
        assertThat(controlRepository.isCreationBlockedForShare()).isFalse();
        StoneCardReindexJobEntity selected = transactionTemplate.execute(status -> controlRepository.activate(jobRepository.save(job(StoneCardReindexTarget.SELECTED))));
        assertThat(controlRepository.findActiveJobId()).contains(selected.getJobId());
        assertThat(controlRepository.isCreationBlockedForShare()).isFalse();
        assertThat(jobRepository.findById(job.getJobId())).isPresent();
    }

    private StoneCardReindexJobEntity job(StoneCardReindexTarget target) {
        StoneCardReindexJobEntity stoneCardReindexJobEntity = new StoneCardReindexJobEntity();
        stoneCardReindexJobEntity.setJobId(UUID.randomUUID());
        stoneCardReindexJobEntity.setTarget(target);
        stoneCardReindexJobEntity.setStatus(StoneCardReindexStatus.PENDING);
        stoneCardReindexJobEntity.setCreatedAt(Instant.parse("2026-10-12T00:00:00Z"));
        stoneCardReindexJobEntity.setUpdatedAt(stoneCardReindexJobEntity.getCreatedAt());
        return stoneCardReindexJobEntity;
    }

    private StoneCardReindexItemEntity item(UUID jobId, long id, String kind, String outcome, String error) {
        StoneCardReindexItemEntity stoneCardReindexItemEntity = new StoneCardReindexItemEntity();
        stoneCardReindexItemEntity.setJobId(jobId);
        stoneCardReindexItemEntity.setStoneId(id);
        stoneCardReindexItemEntity.setWorkKind(kind);
        stoneCardReindexItemEntity.setOutcome(outcome);
        stoneCardReindexItemEntity.setError(error);
        return stoneCardReindexItemEntity;
    }
}
