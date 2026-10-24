package lab.stoneshelter.services;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ModelInvocationAdmissionService {
    private final ModelTokenBudgetTransactionService ledger;
    private final ModelInvocationSettings settings;
    private record Control(Clock clock, AtomicReference<Consumer<Throwable>> failureListener) {}
    private final Control control;
    private final Bulkhead bulkhead;
    public ModelInvocationAdmissionService(ModelTokenBudgetTransactionService ledger, ModelInvocationSettings settings, Clock clock) {
        this.ledger = ledger; this.settings = settings; this.control = new Control(clock, new AtomicReference<>(ignored -> {}));
        bulkhead = Bulkhead.of("shared-model-invocations", BulkheadConfig.custom()
                .maxConcurrentCalls(settings.concurrentCalls()).maxWaitDuration(Duration.ZERO).build());
    }
    public Lease acquire(ModelAdmissionMode mode, long tokens, Instant deadline) {
        long remaining = Math.max(0, Duration.between(control.clock().instant(), deadline).toNanos());
        long stop = System.nanoTime() + remaining;
        for (;;) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= stop)
                throw new ModelAdmissionDeferredException(control.clock().instant());
            if (!bulkhead.tryAcquirePermission()) {
                if (mode == ModelAdmissionMode.CUSTOMER) throw new ModelCapacityExceededException();
                if (mode == ModelAdmissionMode.BACKGROUND) throw new ModelAdmissionDeferredException(control.clock().instant().plusSeconds(2));
            } else {
                try {
                    return new Lease(ledger.reserve(UUID.randomUUID(), LocalDate.now(control.clock().withZone(ZoneOffset.UTC)), tokens, settings.dailyBudget()));
                } catch (RuntimeException exception) {
                    bulkhead.onComplete();
                    if (!(exception instanceof ModelTokenBudgetExceededException exhausted)) { failure(exception); throw exception; }
                    if (mode == ModelAdmissionMode.CUSTOMER) throw exhausted;
                    if (mode == ModelAdmissionMode.BACKGROUND) throw new ModelAdmissionDeferredException(exhausted.getRetryAt());
                }
            }
            try { Thread.sleep(Math.min(100, Math.max(1, (stop - System.nanoTime()) / 1000000))); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ModelAdmissionDeferredException(control.clock().instant()); }
        }
    }
    public void setFailureListener(Consumer<Throwable> listener) { control.failureListener().set(listener); }
    public void failure(Throwable failure) { control.failureListener().get().accept(failure); }
    public void probe() { ledger.probe(); }
    public final class Lease implements AutoCloseable {
        private ModelTokenReservationEntity reservation;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(ModelTokenReservationEntity reservation) { this.reservation = reservation; }
        public UUID id() { return reservation.id(); }
        public void settle(long actual) {
            try { reservation = ledger.settle(reservation.id(), actual); }
            catch (RuntimeException exception) { failure(exception); throw exception; }
        }
        @Override public void close() { if (closed.compareAndSet(false, true)) bulkhead.onComplete(); }
    }
}
