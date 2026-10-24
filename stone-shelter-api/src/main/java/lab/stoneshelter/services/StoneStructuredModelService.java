package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lab.stoneshelter.exceptions.StoneModelProviderUnavailableException;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.repositories.OllamaChatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Shared guarded invocation for new formats, using the existing client/ledger. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneStructuredModelService {
    private enum Phase { PROVIDER, LEDGER, EXIT }
    private record Components(OllamaChatRepository repository, ModelInvocationAdmissionService admission,
            StoneExactSearchSettings settings, Clock clock) {}
    private final Components components;

    public StoneStructuredModelService(OllamaChatRepository repository, ModelInvocationAdmissionService admission,
            StoneExactSearchSettings settings, Clock clock) {
        components = new Components(repository, admission, settings, clock);
    }

    public boolean fits(String system, String message, int output, int repairAllowance) {
        return (long) OllamaChatRepository.promptUpperBound(invocation(system, message, Map.of(),
                components.clock().instant().plusSeconds(1), output)) + output + repairAllowance <= components.settings().contextTokens();
    }

    public boolean fits(String system, String message, int output, int repairAllowance, List<StoneChatTurnEntity> history) {
        StoneModelInvocationEntity invocation = invocation(system, message, Map.of(), components.clock().instant().plusSeconds(1), output);
        invocation.setHistory(history);
        return (long) OllamaChatRepository.promptUpperBound(invocation) + output + repairAllowance <= components.settings().contextTokens();
    }

    public StoneModelResponseEntity invoke(String system, String message, Map<String, Object> schema, Instant deadline, int output) {
        return invoke(system, message, schema, deadline, output, List.of());
    }

    public StoneModelResponseEntity invoke(String system, String message, Map<String, Object> schema, Instant deadline,
            int output, List<StoneChatTurnEntity> history) {
        if (!components.clock().instant().isBefore(deadline)) throw new StoneChatUnavailableException();
        Instant callDeadline = components.clock().instant().plus(components.settings().callTimeout());
        if (deadline.isBefore(callDeadline)) callDeadline = deadline;
        StoneModelInvocationEntity stoneModelInvocationEntity = invocation(system, message, schema, callDeadline, output);
        stoneModelInvocationEntity.setHistory(history);
        if (!fits(system, message, output, 0, history)) throw new StoneSemanticSearchLimitExceededException();
        var lease = components.admission().acquire(ModelAdmissionMode.CUSTOMER, components.settings().contextTokens(), callDeadline);
        CompletableFuture<StoneModelResponseEntity> completion = new CompletableFuture<>();
        Thread worker;
        AtomicReference<Phase> phase = new AtomicReference<>(Phase.PROVIDER);
        try {
            worker = Thread.ofVirtual().name("structured-stone-model").start(() -> {
                StoneModelResponseEntity response = null;
                RuntimeException failure = null;
                boolean started = false;
                try (lease) {
                    components.repository().probe(components.settings().endpoint());
                    started = true;
                    response = components.repository().invoke(stoneModelInvocationEntity);
                    phase.set(Phase.LEDGER);
                    lease.settle((long) response.inputTokens() + response.outputTokens());
                    phase.set(Phase.PROVIDER);
                    components.repository().probe(components.settings().endpoint());
                } catch (RuntimeException exception) {
                    failure = phase.get() == Phase.PROVIDER ? new StoneModelProviderUnavailableException(lease.id(), exception) : exception;
                    if (!started) {
                        try { lease.settle(0); } catch (RuntimeException settlement) { exception.addSuppressed(settlement); }
                    }
                    components.admission().failure(failure);
                }
                phase.set(Phase.EXIT);
                // Publish only after actual invocation/lease cleanup, never before it.
                if (failure == null) completion.complete(response);
                else completion.completeExceptionally(failure);
            });
        } catch (RuntimeException exception) {
            try { lease.settle(0); } finally { lease.close(); }
            throw exception;
        }
        try {
            return completion.get(Math.max(1, Duration.between(components.clock().instant(), callDeadline).toNanos()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            worker.interrupt(); Thread.currentThread().interrupt();
            RuntimeException failure = phase.get() == Phase.PROVIDER ? new StoneModelProviderUnavailableException(lease.id(), exception) : new StoneChatUnavailableException(exception);
            components.admission().failure(failure); throw failure;
        } catch (TimeoutException exception) {
            worker.interrupt();
            RuntimeException failure = phase.get() == Phase.PROVIDER ? new StoneModelProviderUnavailableException(lease.id(), exception) : new StoneChatUnavailableException(exception);
            components.admission().failure(failure); throw failure;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw new StoneChatUnavailableException(exception);
        }
    }

    public void probe() { components.repository().probe(components.settings().endpoint()); }
    public int contextTokens() { return components.settings().contextTokens(); }
    public int checkingOutputTokens() { return components.settings().outputTokens(); }

    private StoneModelInvocationEntity invocation(String system, String message, Map<String, Object> schema, Instant deadline, int output) {
        if (output < 1 || output >= components.settings().contextTokens()) throw new IllegalArgumentException("Invalid structured output allowance");
        StoneModelInvocationEntity stoneModelInvocationEntity = new StoneModelInvocationEntity();
        stoneModelInvocationEntity.setEndpoint(components.settings().endpoint());
        stoneModelInvocationEntity.setSystem(system); stoneModelInvocationEntity.setMessage(message);
        stoneModelInvocationEntity.setHistory(List.of()); stoneModelInvocationEntity.setSchema(schema);
        stoneModelInvocationEntity.setDeadline(deadline); stoneModelInvocationEntity.setOutputTokens(output);
        stoneModelInvocationEntity.setContextTokens(components.settings().contextTokens());
        stoneModelInvocationEntity.setIncludeExactSearchExample(false);
        return stoneModelInvocationEntity;
    }
}
