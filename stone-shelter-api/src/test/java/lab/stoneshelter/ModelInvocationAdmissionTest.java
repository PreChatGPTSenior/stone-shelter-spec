package lab.stoneshelter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class ModelInvocationAdmissionTest {
    private final ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
    private final Clock clock = Clock.systemUTC();
    private ModelInvocationAdmissionService service() {
        when(ledger.reserve(any(), any(), anyLong(), anyLong())).thenAnswer(call ->
                new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        return new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), clock);
    }
    @Test void sharedSlotRejectsCustomerAndDefersBackgroundWithoutExecuting() {
        var service = service();
        try (var lease = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) {
            assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.CUSTOMER, 32768, clock.instant().plusSeconds(2)))
                    .isInstanceOf(ModelCapacityExceededException.class);
            assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.BACKGROUND, 32768, clock.instant().plusSeconds(2)))
                    .isInstanceOf(ModelAdmissionDeferredException.class);
        }
        try (var lease = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) { assertThat(lease.id()).isNotNull(); }
    }
    @Test void exhaustedBudgetRejectsCustomerAndDefersBackgroundAndReleasesPermit() {
        var service = service(); Instant nextDay = LocalDate.now(clock).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        doThrow(new ModelTokenBudgetExceededException(nextDay)).when(ledger).reserve(any(), any(), anyLong(), anyLong());
        assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2)))
                .isInstanceOf(ModelTokenBudgetExceededException.class);
        assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.BACKGROUND, 32768, clock.instant().plusSeconds(2)))
                .isInstanceOf(ModelAdmissionDeferredException.class);
    }
    @Test void importWaitsWithoutHoldingPermitAndHonorsDeadline() throws Exception {
        var service = service(); var occupied = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(3));
        CompletableFuture<UUID> waiting = CompletableFuture.supplyAsync(() -> {
            try (var lease = service.acquire(ModelAdmissionMode.IMPORT, 32768, clock.instant().plusSeconds(3))) { return lease.id(); }
        });
        assertThat(waiting.isDone()).isFalse(); occupied.close(); assertThat(waiting.get(2, TimeUnit.SECONDS)).isNotNull();
        try (var lease = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) {
            assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.IMPORT, 32768, clock.instant().plusMillis(50)))
                    .isInstanceOf(ModelAdmissionDeferredException.class);
        }
    }
    @Test void callerTimeoutDoesNotReleasePermitUntilActualExecutionExits() throws Exception {
        var service = service(); CountDownLatch started = new CountDownLatch(1); CountDownLatch finish = new CountDownLatch(1);
        var lease = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2));
        CompletableFuture<Void> actual = CompletableFuture.runAsync(() -> {
            try (lease) { started.countDown(); try { finish.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        });
        try {
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> actual.get(10, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2)))
                    .isInstanceOf(ModelCapacityExceededException.class);
        } finally { finish.countDown(); actual.get(2, TimeUnit.SECONDS); }
        try (var admitted = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) { assertThat(admitted.id()).isNotNull(); }
    }
    @Test void settlementFailureSignalsControlOutageWithoutRefundingUnknownUsage() {
        var service = service(); var failures = new java.util.concurrent.atomic.AtomicInteger(); service.setFailureListener(ignored -> failures.incrementAndGet());
        doThrow(new IllegalStateException("settlement storage unavailable")).when(ledger).settle(any(), anyLong());
        try (var lease = service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) {
            assertThatThrownBy(() -> lease.settle(20)).isInstanceOf(IllegalStateException.class); assertThat(failures.get()).isEqualTo(1);
        }
    }
    @Test void ledgerFailureReleasesSlotAndPropagatesControlFailure() {
        var service = service(); doThrow(new IllegalStateException("ledger offline")).when(ledger).reserve(any(), any(), anyLong(), anyLong());
        var failures = new java.util.concurrent.atomic.AtomicInteger();
        service.setFailureListener(ignored -> failures.incrementAndGet());
        assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failures.get()).isEqualTo(1);
        assertThatThrownBy(() -> service.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(failures.get()).isEqualTo(2);
    }
}
