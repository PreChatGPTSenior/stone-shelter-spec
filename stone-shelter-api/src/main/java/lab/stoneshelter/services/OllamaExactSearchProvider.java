package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import lab.stoneshelter.enums.ModelAdmissionMode;
import org.springframework.beans.factory.annotation.Autowired;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.repositories.OllamaChatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional
public class OllamaExactSearchProvider implements StoneExactSearchProvider {
    private final OllamaChatRepository repository;
    private final StoneExactSearchSettings settings;
    private final Clock clock;
    private ModelInvocationAdmissionService admission;
    @Autowired public void configureAdmission(ModelInvocationAdmissionService admission) { this.admission = admission; }
    public OllamaExactSearchProvider(OllamaChatRepository repository, StoneExactSearchSettings settings, Clock clock) {
        this.repository = repository; this.settings = settings; this.clock = clock;
    }
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneModelResponseEntity invoke(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history,
            Instant deadline, String repairReason) {
        StoneModelInvocationEntity invocation = new StoneModelInvocationEntity();
        invocation.setEndpoint(settings.endpoint());
        invocation.setSystem(settings.systemInstructions(LocalDate.now(clock.withZone(ZoneOffset.UTC)), repairReason));
        invocation.setMessage(turn.getMessage()); invocation.setHistory(List.of());
        invocation.setContextTokens(settings.contextTokens()); invocation.setOutputTokens(settings.outputTokens());
        invocation.setSchema(settings.schemaFor(turn.getMessage(), inheritedNarrowing(history)));
        invocation.setDeadline(deadline.isBefore(clock.instant().plus(settings.callTimeout()))
                ? deadline : clock.instant().plus(settings.callTimeout()));
        long promptTokens = (long) OllamaChatRepository.promptUpperBound(invocation) + settings.outputTokens();
        if (promptTokens > settings.contextTokens()) throw new StoneChatUnavailableException();
        int first = history.size();
        while (first > 0) {
            StoneChatTurnEntity pair = history.get(first - 1);
            long pairTokens = (long) pair.getMessage().getBytes(StandardCharsets.UTF_8).length
                    + pair.getText().getBytes(StandardCharsets.UTF_8).length + 64;
            if (promptTokens + pairTokens > settings.contextTokens()) break;
            promptTokens += pairTokens; first--;
        }
        invocation.setHistory(List.copyOf(history.subList(first, history.size())));
        if (admission == null) throw new StoneChatUnavailableException();
        var lease = admission.acquire(ModelAdmissionMode.CUSTOMER, settings.contextTokens(), invocation.getDeadline());
        CompletableFuture<StoneModelResponseEntity> completion = new CompletableFuture<>();
        Thread execution;
        try {
            execution = Thread.ofVirtual().name("exact-search-model").start(() -> {
                boolean started = false;
                try (lease) {
                    repository.probe(settings.endpoint());
                    started = true;
                    StoneModelResponseEntity response = repository.invoke(invocation);
                    lease.settle((long) response.inputTokens() + response.outputTokens());
                    repository.probe(settings.endpoint());
                    completion.complete(response);
                } catch (RuntimeException exception) {
                    if (!started) {
                        try { lease.settle(0); } catch (RuntimeException settlement) { exception.addSuppressed(settlement); }
                    }
                    admission.failure(exception); completion.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            try { lease.settle(0); } finally { lease.close(); }
            throw exception;
        }
        try {
            return completion.get(Math.max(1, Duration.between(clock.instant(), invocation.getDeadline()).toNanos()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            execution.interrupt(); Thread.currentThread().interrupt(); admission.failure(exception); throw new StoneChatUnavailableException(exception);
        } catch (TimeoutException exception) {
            execution.interrupt(); admission.failure(exception); throw new StoneChatUnavailableException(exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new StoneChatUnavailableException(exception);
        }
    }
    private boolean inheritedNarrowing(List<StoneChatTurnEntity> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).getExactSearchCriteria() != null) return history.get(index).getExactSearchCriteria().hasNarrowing();
        }
        return false;
    }
    @Override public void probe() { repository.probe(settings.endpoint()); }
}
