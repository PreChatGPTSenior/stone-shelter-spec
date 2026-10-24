package lab.stoneshelter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.stoneshelter.criteria.StoneDocumentIndexOperationCriteria;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentIndexConflictException;
import lab.stoneshelter.repositories.StoneDocumentIndexRepository;
import lab.stoneshelter.services.StoneDocumentPreparationService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.services.StoneDocumentIndexTransactionService;
import lab.stoneshelter.services.StoneEncyclopediaIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import lab.stoneshelter.services.StoneCardReindexRunnerService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.OutboxDispatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;

@SpringBootTest
class StoneDocumentIndexLifecycleTest extends IntegrationTest {
    @MockitoBean private OutboxDispatcherService isolatedOutboxDispatcherService;
    @MockitoBean private OutboxKafkaTopicService isolatedOutboxKafkaTopicService;
    @MockitoBean private StoneIndexKafkaTopicService isolatedStoneIndexKafkaTopicService;
    @MockitoBean private StoneIndexConsumerService isolatedStoneIndexConsumerService;
    @MockitoBean private StoneCardReindexRunnerService isolatedStoneCardReindexRunnerService;
    @Autowired private StoneEncyclopediaIndexService service;
    @Autowired private StoneDocumentIndexTransactionService transactions;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private StoneDocumentIndexRepository repository;
    @MockitoBean private StoneDocumentPreparationService preparation;
    @MockitoBean private StoneDocumentIndexSettings settings;
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void prepare() throws Exception {
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(transactions), "clock", clock);
        when(settings.importTimeout()).thenReturn(Duration.ofHours(2));
        when(preparation.prepare(any())).thenAnswer(invocation -> List.of(fragment("a", "Care", 0)));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stone_document_fragment WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("UPDATE stone_document_index SET active_operation_key = NULL, owner_token = NULL WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("DELETE FROM stone_document_index_operation WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("DELETE FROM stone_document_index WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
    }

    @Test
    void replacementRemovesOldSectionsAndOldReplayNeverRestoresThem() {
        UUID first = UUID.randomUUID();
        assertThat(service.importIndex(first).outcome()).isEqualTo(StoneDocumentIndexOutcome.CREATED);
        when(preparation.prepare(any())).thenReturn(List.of(fragment("b", "New topic", 0)));
        assertThat(service.importIndex(UUID.randomUUID()).outcome()).isEqualTo(StoneDocumentIndexOutcome.REPLACED);
        assertThat(service.importIndex(first).sourceVersion()).isEqualTo("a".repeat(64));
        assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).singleElement()
                .satisfies(f -> assertThat(f.getSectionPath()).isEqualTo("New topic"));
        verify(preparation, times(2)).prepare(any());
    }

    @Test
    void deletionReplayDoesNotRemoveLaterIndexAndAbsentDeletionIsSuccessful() {
        UUID deletion = UUID.randomUUID();
        assertThat(service.delete(deletion).id()).isEqualTo(StoneDocumentIndexEntity.DOCUMENT_KEY);
        service.importIndex(UUID.randomUUID());
        service.delete(deletion);
        assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).hasSize(1);
        service.delete(UUID.randomUUID());
        assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).isEmpty();
        assertThat(repository.findOperation(deletion)).isPresent();
    }

    @Test
    void failedPreparationPreservesPreviousProjectionAndMakesKeyTerminal() {
        service.importIndex(UUID.randomUUID());
        UUID failed = UUID.randomUUID();
        when(preparation.prepare(any())).thenThrow(new StoneDocumentDependencyUnavailableException("provider unavailable"));
        assertThatThrownBy(() -> service.importIndex(failed)).isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        assertThat(repository.findById(StoneDocumentIndexEntity.DOCUMENT_KEY).orElseThrow().getSourceVersion()).isEqualTo("a".repeat(64));
        assertThatThrownBy(() -> service.importIndex(failed)).isInstanceOf(StoneDocumentIndexConflictException.class).hasMessageContaining("failed");
    }

    @Test
    void publicationRollbackPreservesMetadataAndAllFragments() {
        service.importIndex(UUID.randomUUID());
        StoneDocumentFragmentEntity bad = fragment("b", "Bad", 0); bad.setEmbedding(new double[2]);
        when(preparation.prepare(any())).thenReturn(List.of(bad));
        assertThatThrownBy(() -> service.importIndex(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).singleElement()
                .satisfies(f -> assertThat(f.getSourceVersion()).isEqualTo("a".repeat(64)));
        assertThat(repository.findById(StoneDocumentIndexEntity.DOCUMENT_KEY).orElseThrow().getSourceVersion()).isEqualTo("a".repeat(64));
    }

    @Test
    void keysExpirePermanentlyAndCannotCrossOperationKinds() {
        UUID key = UUID.randomUUID(); service.importIndex(key);
        assertThatThrownBy(() -> service.delete(key)).isInstanceOf(StoneDocumentIndexConflictException.class).hasMessageContaining("different");
        clock.instant = clock.instant.plus(Duration.ofDays(3));
        assertThatThrownBy(() -> service.importIndex(key)).isInstanceOf(StoneDocumentIndexConflictException.class).hasMessageContaining("expired");
        assertThat(repository.findOperation(key).orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.EXPIRED);
        assertThat(repository.findOperation(key).orElseThrow().getSourceVersion()).isNull();
        service.delete(UUID.randomUUID());
        assertThatThrownBy(() -> service.importIndex(key)).isInstanceOf(StoneDocumentIndexConflictException.class);
    }

    @Test
    void busyRequestDoesNotConsumeNewKeyAndActiveRetryIsRejected() throws Exception {
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        when(preparation.prepare(any())).thenAnswer(invocation -> {
            entered.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return List.of(fragment("a", "Care", 0));
        });
        UUID active = UUID.randomUUID(); UUID reusable = UUID.randomUUID();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> service.importIndex(active));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.delete(reusable)).isInstanceOf(StoneDocumentIndexConflictException.class)
                        .hasMessage("This operation is already running.");
                assertThatThrownBy(() -> service.importIndex(active)).isInstanceOf(StoneDocumentIndexConflictException.class);
                assertThat(repository.findOperation(reusable)).isEmpty();
            } finally { release.countDown(); }
            future.get(10, TimeUnit.SECONDS);
        }
        service.delete(reusable);
        assertThat(repository.findOperation(reusable).orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.SUCCEEDED);
    }

    @Test
    void deadlineDoesNotReleaseOwnershipUntilWorkerExits() throws Exception {
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        when(preparation.prepare(any())).thenAnswer(invocation -> {
            entered.countDown(); release.await(10, TimeUnit.SECONDS); return List.of(fragment("a", "Care", 0));
        });
        UUID active = UUID.randomUUID();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> service.importIndex(active));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                clock.instant = clock.instant.plus(Duration.ofHours(3));
                assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(StoneDocumentIndexConflictException.class);
            } finally { release.countDown(); }
            assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(StoneDocumentDependencyUnavailableException.class);
        }
        assertThat(repository.findOperation(active).orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.FAILED);
        assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).isEmpty();
    }

    @Test
    void verifiedStoppedRecoveryRejectsStaleOwnerAndOldKey() {
        UUID key = UUID.randomUUID(); UUID owner = UUID.randomUUID();
        transactions.claim(new StoneDocumentIndexOperationCriteria(key, StoneDocumentIndexOperationKind.IMPORT, owner, clock.instant.plusSeconds(30)));
        assertThatThrownBy(() -> transactions.reconcileStopped(key, null)).isInstanceOf(StoneDocumentIndexConflictException.class);
        assertThatThrownBy(() -> transactions.reconcileStopped(key, UUID.randomUUID())).isInstanceOf(StoneDocumentIndexConflictException.class);
        assertThat(repository.findById(StoneDocumentIndexEntity.DOCUMENT_KEY).orElseThrow().getOwnerToken()).isEqualTo(owner);
        transactions.reconcileStopped(key, owner);
        assertThatThrownBy(() -> transactions.publish(key, owner, List.of(fragment("a", "Care", 0)))).isInstanceOf(StoneDocumentIndexConflictException.class);
        assertThatThrownBy(() -> service.importIndex(key)).isInstanceOf(StoneDocumentIndexConflictException.class);
    }

    @Test
    void lostCommitAcknowledgementReconcilesSuccessWithoutRepeatingPublication() {
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { throw new DataAccessResourceFailureException("lost commit acknowledgement"); }
            });
            return result;
        }).when(repository).insertFragments(any());
        UUID key = UUID.randomUUID();
        assertThat(service.importIndex(key).outcome()).isEqualTo(StoneDocumentIndexOutcome.CREATED);
        assertThat(service.importIndex(key).indexedFragments()).isEqualTo(1);
        assertThat(repository.findOperation(key).orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.SUCCEEDED);
        verify(preparation, times(1)).prepare(any());
        verify(repository, times(1)).insertFragments(any());
    }

    @Test
    void uncertainReconciliationRetainsGuardUntilVerifiedRecovery() {
        AtomicInteger locks = new AtomicInteger();
        doAnswer(invocation -> locks.incrementAndGet() == 1 ? invocation.callRealMethod() : false)
                .when(repository).tryMutationLock();
        when(preparation.prepare(any())).thenThrow(new StoneDocumentDependencyUnavailableException("provider stopped"));
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> service.importIndex(key)).isInstanceOf(StoneDocumentDependencyUnavailableException.class)
                .hasMessageContaining("uncertain");
        assertThat(repository.findOperation(key).orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.RUNNING);
        UUID owner = repository.findOperation(key).orElseThrow().getOwnerToken();
        reset(repository);
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(StoneDocumentIndexConflictException.class);
        transactions.reconcileStopped(key, owner);
        assertThat(repository.findOperation(key).orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.FAILED);
    }

    @Test
    void readersSeePreviousCompleteProjectionUntilCommit() throws Exception {
        service.importIndex(UUID.randomUUID());
        when(preparation.prepare(any())).thenReturn(List.of(fragment("b", "Replacement", 0)));
        CountDownLatch inserted = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod(); inserted.countDown(); release.await(10, TimeUnit.SECONDS); return result;
        }).when(repository).insertFragments(any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> service.importIndex(UUID.randomUUID()));
            try {
                assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(repository.findById(StoneDocumentIndexEntity.DOCUMENT_KEY).orElseThrow().getSourceVersion()).isEqualTo("a".repeat(64));
                assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).singleElement()
                        .satisfies(f -> assertThat(f.getSourceVersion()).isEqualTo("a".repeat(64)));
            } finally { release.countDown(); }
            future.get(10, TimeUnit.SECONDS);
        }
        assertThat(repository.findFragments(StoneDocumentIndexEntity.DOCUMENT_KEY)).singleElement()
                .satisfies(f -> assertThat(f.getSourceVersion()).isEqualTo("b".repeat(64)));
    }

    private StoneDocumentFragmentEntity fragment(String hash, String section, int order) {
        StoneDocumentFragmentEntity fragment = new StoneDocumentFragmentEntity();
        fragment.setDocumentKey(StoneDocumentIndexEntity.DOCUMENT_KEY); fragment.setSourceVersion(hash.repeat(64));
        fragment.setLanguage("en"); fragment.setEmbeddingProfile("profile"); fragment.setSectionPath(section);
        fragment.setText("Guide > Basalt > " + section); fragment.setFragmentOrder(order);
        double[] vector = new double[1024]; vector[0] = 1; fragment.setEmbedding(vector);
        return fragment;
    }

    private static class MutableClock extends Clock {
        private volatile Instant instant = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
