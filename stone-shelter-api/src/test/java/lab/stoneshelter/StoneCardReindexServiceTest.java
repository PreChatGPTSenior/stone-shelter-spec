package lab.stoneshelter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneCardReindexStatus;
import lab.stoneshelter.enums.StoneCardReindexTarget;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.StoneCardReindexConflictException;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.repositories.StoneCardReindexControlRepository;
import lab.stoneshelter.repositories.StoneCardReindexItemRepository;
import lab.stoneshelter.repositories.StoneCardReindexJobRepository;
import lab.stoneshelter.services.OutboxDispatcherService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.StoneCardReindexRunnerService;
import lab.stoneshelter.services.StoneCardReindexSettings;
import lab.stoneshelter.services.StoneCardReindexTransactionService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.StoneService;
import lab.stoneshelter.services.StoneCreationService;
import lab.stoneshelter.shared.StoneCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class StoneCardReindexServiceTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StoneService stoneService;
    @Autowired private StoneCreationService stoneCreationService;
    @Autowired private StoneCardEmbeddingRepository embeddingRepository;
    @Autowired private StoneCardReindexJobRepository jobRepository;
    @Autowired private StoneCardReindexItemRepository itemRepository;
    @Autowired private StoneCardReindexControlRepository controlRepository;
    @Autowired private StoneCardReindexSettings settings;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private StoneCardReindexTransactionService transactionService;
    @MockitoBean private StoneCardReindexRunnerService automaticRunner;
    @MockitoBean private OutboxDispatcherService outboxDispatcherService;
    @MockitoBean private OutboxKafkaTopicService outboxKafkaTopicService;
    @MockitoBean private StoneIndexConsumerService stoneIndexConsumerService;
    @MockitoBean private StoneIndexKafkaTopicService stoneIndexKafkaTopicService;
    @MockitoBean private OllamaEmbeddingRepository ollamaEmbeddingRepository;
    private final List<Long> createdIds = new ArrayList<>();

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) { registry.add("STONE_REINDEX_BATCH_SIZE", () -> "1"); }

    @AfterEach
    void cleanup() {
        reset((StoneCardReindexTransactionService) AopTestUtils.getUltimateTargetObject(transactionService));
        jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT IF EXISTS reindex_test_reject_snapshot");
        jdbcTemplate.update("UPDATE stone_card_reindex_control SET active_job = NULL, creation_blocked = FALSE");
        jdbcTemplate.update("DELETE FROM stone_card_reindex_item");
        jdbcTemplate.update("DELETE FROM stone_card_reindex_job");
        for (long id : createdIds) {
            jdbcTemplate.update("DELETE FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", id);
            jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id = ?", id);
            jdbcTemplate.update("DELETE FROM stone_card_index_state WHERE stone_id = ?", id);
            jdbcTemplate.update("DELETE FROM stone_index_source_version WHERE stone_id = ?", id);
            jdbcTemplate.update("DELETE FROM stone WHERE id = ?", id);
        }
    }

    @Test
    void defaultAndConfiguredBoundsForceFreshVersionsAndResumeWithoutDuplicatingItems() {
        assertThat(new StoneCardReindexSettings(new MockEnvironment()).batchSize()).isEqualTo(20);
        long first = create();
        long second = create();
        var job = transactionService.start(StoneCardReindexTarget.SELECTED, List.of(first, second, first));
        assertThat(job.getTotalCount()).isEqualTo(2);
        runner().runBatch();
        assertThat(jobRepository.findById(job.getJobId()).orElseThrow().getProcessedCount()).isEqualTo(1);
        runner().runBatch();
        var finished = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(StoneCardReindexStatus.ENQUEUED);
        assertThat(finished.getEnqueuedCount()).isEqualTo(2);
        assertThat(countEvents(first)).isEqualTo(2);
        assertThat(countEvents(second)).isEqualTo(2);
        runner().runBatch();
        assertThat(countEvents(first)).isEqualTo(2);
        assertThat(controlRepository.findActiveJobId()).isEmpty();
        verifyNoInteractions(ollamaEmbeddingRepository);
    }

    @Test
    void fullJobPersistsCreationGateAndReleasesItAtEnqueuedWithoutModelOrKafkaWaiting() {
        long id = create();
        var job = transactionService.start(StoneCardReindexTarget.ALL, List.of());
        assertThat(new StoneCardReindexControlRepository(jdbcTemplate).isCreationBlockedForShare()).isTrue();
        assertThatThrownBy(() -> stoneService.create(request())).isInstanceOf(StoneCardReindexConflictException.class);
        runner().runBatch();
        assertThat(jobRepository.findById(job.getJobId()).orElseThrow().getStatus()).isEqualTo(StoneCardReindexStatus.ENQUEUED);
        assertThat(controlRepository.isCreationBlockedForShare()).isFalse();
        create();
        assertThat(countEvents(id)).isEqualTo(2);
        verifyNoInteractions(ollamaEmbeddingRepository);
    }

    @Test
    void targetedJobDoesNotBlockCreationAndDisappearedCardsAreSkipped() {
        long id = create();
        var job = transactionService.start(StoneCardReindexTarget.SELECTED, List.of(id));
        create();
        stoneService.delete(id);
        runner().runBatch();
        var finished = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(finished.getSkippedCount()).isEqualTo(1);
        assertThat(finished.getEnqueuedCount()).isZero();
        assertThat(finished.getStatus()).isEqualTo(StoneCardReindexStatus.ENQUEUED);
    }

    @Test
    void fullReconciliationPreservesVectorsAndEnqueuesDeleteAboveCounterAndProcessedVersion() {
        long live = create();
        long orphan = 900001;
        createdIds.add(orphan);
        assertThat(embeddingRepository.save(vector(live)).getStoneId()).isEqualTo(live);
        assertThat(embeddingRepository.save(vector(orphan)).getStoneId()).isEqualTo(orphan);
        jdbcTemplate.update("INSERT INTO stone_index_source_version VALUES (?, 5)", orphan);
        jdbcTemplate.update("INSERT INTO stone_card_index_state VALUES (?, 7, TRUE)", orphan);
        var job = transactionService.start(StoneCardReindexTarget.ALL, List.of());
        runner().runBatch();
        runner().runBatch();
        var finished = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(StoneCardReindexStatus.ENQUEUED);
        assertThat(finished.getReconciledCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT (payload->>'sourceVersion')::bigint FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", Long.class, orphan)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT source_version FROM stone_card_index_state WHERE stone_id = ?", Long.class, orphan)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding WHERE stone_id IN (?, ?)", Long.class, live, orphan)).isEqualTo(2);
    }

    @Test
    void cardErrorsAreIndependentButStorageOutagesKeepItemsPending() {
        long first = create();
        long second = create();
        var job = transactionService.start(StoneCardReindexTarget.SELECTED, List.of(first, second));
        jdbcTemplate.execute("ALTER TABLE outbox_event ADD CONSTRAINT reindex_test_reject_snapshot CHECK ((payload->>'stoneId')::bigint <> "
                + first + " OR (payload->>'sourceVersion')::bigint = 1)");
        runner().runBatch();
        assertThat(jobRepository.findById(job.getJobId()).orElseThrow().getFailedCount()).isEqualTo(1);
        doThrow(new DataAccessResourceFailureException("PostgreSQL unavailable")).when((StoneCardReindexTransactionService) AopTestUtils.getUltimateTargetObject(transactionService)).process(any());
        runner().runBatch();
        assertThat(itemRepository.findPending(job.getJobId(), 20)).hasSize(1);
        assertThat(jobRepository.findById(job.getJobId()).orElseThrow().getStatus()).isEqualTo(StoneCardReindexStatus.RUNNING);
        reset((StoneCardReindexTransactionService) AopTestUtils.getUltimateTargetObject(transactionService));
        runner().runBatch();
        var finished = jobRepository.findById(job.getJobId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(StoneCardReindexStatus.ENQUEUED);
        assertThat(finished.getFailedCount()).isEqualTo(1);
        assertThat(finished.getEnqueuedCount()).isEqualTo(1);
        assertThat(countEvents(first)).isEqualTo(1);
        assertThat(countEvents(second)).isEqualTo(2);
    }

    @Test
    void fullStartWaitsForInflightCreationBeforeCapturingItsManifest() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch starting = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var creation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertThat(controlRepository.isCreationBlockedForShare()).isFalse();
                held.countDown();
                try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException exception) { throw new IllegalStateException(exception); }
                return stoneCreationService.create(request());
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var fullStart = executor.submit(() -> {
                starting.countDown();
                return transactionService.start(StoneCardReindexTarget.ALL, List.of());
            });
            assertThat(starting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> fullStart.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            long id = creation.get(5, TimeUnit.SECONDS);
            createdIds.add(id);
            var job = fullStart.get(5, TimeUnit.SECONDS);
            assertThat(itemRepository.findPending(job.getJobId(), 20)).extracting(item -> item.getStoneId()).contains(id);
        } finally { release.countDown(); }
    }

    private StoneCardReindexRunnerService runner() { return new StoneCardReindexRunnerService(transactionService, settings); }
    private long create() {
        long id = stoneService.create(request()).getId();
        createdIds.add(id);
        return id;
    }
    private StoneCreateRequest request() {
        StoneCreateRequest request = new StoneCreateRequest();
        request.setName("Luna");
        request.setStoneType(StoneType.BASALT);
        request.setStoneSize(StoneSize.SMALL);
        request.setAdoptionStatus(AdoptionStatus.AVAILABLE);
        return request;
    }
    private long countEvents(long id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event WHERE (payload->>'stoneId')::bigint = ?", Long.class, id);
    }
    private StoneCardEmbeddingEntity vector(long id) {
        StoneCardEmbeddingEntity entity = new StoneCardEmbeddingEntity();
        entity.setStoneId(id);
        entity.setText("Existing vector");
        entity.setEmbeddingProfile("profile");
        double[] vector = new double[1024];
        vector[0] = 1;
        entity.setEmbedding(vector);
        return entity;
    }
}
