package lab.stoneshelter.services;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.exceptions.StoneModelProviderUnavailableException;
import lab.stoneshelter.entities.StoneRagPreparedTurnEntity;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.repositories.StoneRagKnowledgeRepository;
import java.time.Instant;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lab.stoneshelter.exceptions.StoneChatConversationBusyException;
import lab.stoneshelter.exceptions.StoneChatStreamCapacityExceededException;
import lab.stoneshelter.exceptions.StoneChatConversationNotFoundException;
import lab.stoneshelter.exceptions.StoneChatOriginRejectedException;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.exceptions.StoneChatTurnConflictException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneChatAvailabilityService {
    private record Controls(StoneChatActiveTurnRepository leases, StoneChatContextSelector context) {}
    private record Components(StoneChatSessionRepository sessions, StoneChatConversationRepository history,
                              StoneChatRateLimiterRepository limiter, Controls controls) {}
    private record Status(long generation, long controlEpoch, boolean ready) {}
    private record Tracking(AtomicReference<Status> status, AtomicBoolean probing) {}
    private record Models(StoneExactSearchProvider provider, ModelInvocationAdmissionService admission) {}
    private Models models;
    @Autowired public void configureModels(StoneExactSearchProvider provider, ModelInvocationAdmissionService admission) {
        models = new Models(provider, admission); admission.setFailureListener(this::modelFailure);
    }
    private StoneRagKnowledgeRepository ragControls;
    @Autowired public void configureRagControls(StoneRagKnowledgeRepository ragControls) { this.ragControls = ragControls; }
    private final Components components;
    private final StoneChatRedisRepository redis;
    private final StoneChatConversationSerializationService serialization;
    private final Tracking tracking = new Tracking(new AtomicReference<>(new Status(0, 0, false)), new AtomicBoolean());
    public StoneChatAvailabilityService(StoneChatSessionRepository sessions, StoneChatConversationRepository history,
            StoneChatRateLimiterRepository limiter, StoneChatActiveTurnRepository leases,
            StoneChatRedisRepository redis, StoneChatConversationSerializationService serialization, StoneChatContextSelector context) {
        this.components = new Components(sessions, history, limiter, new Controls(leases, context));
        this.redis = redis; this.serialization = serialization;
        redis.setFailureListener(this::invalidate);
    }
    public void invalidate(Throwable failure) {
        tracking.status().updateAndGet(previous -> new Status(previous.generation() + 1, previous.controlEpoch() + 1, false));
    }
    private void modelFailure(Throwable failure) {
        if (failure instanceof StoneModelProviderUnavailableException)
            tracking.status().updateAndGet(previous -> new Status(previous.generation() + 1, previous.controlEpoch(), false));
        else invalidate(failure);
    }
    public long captureSelectionControlEpoch() { assertReady(); return tracking.status().get().controlEpoch(); }
    public void assertCompletion(StoneRagPreparedTurnEntity prepared, String sessionId, UUID conversationId) {
        if (prepared.completionEvidence() == null) { assertReady(); return; }
        var evidence = prepared.completionEvidence();
        boolean providerFailure = evidence.failure() instanceof StoneModelProviderUnavailableException;
        if (prepared.turn().getRagState() == null || prepared.turn().getRagState().mode() != StoneRagMode.SELECTION
                || prepared.turn().getRagState().completion() != StoneRagCompletionKind.TEMPLATE || prepared.turn().getStones().isEmpty()
                || evidence.selection() == null || evidence.selection().selection() == null || evidence.selection().selection().getCandidates().isEmpty()
                || !prepared.turn().getTurnId().equals(evidence.selection().turn().getTurnId()) || ragControls == null
                || !Instant.now().isBefore(evidence.deadline()) || evidence.controlEpoch() != tracking.status().get().controlEpoch()
                || !providerFailure && !tracking.status().get().ready()) throw new StoneChatUnavailableException();
        if (!(providerFailure || evidence.failure() instanceof InvalidStoneRagOutputException || evidence.failure() instanceof StoneRagAnswerLimitExceededException
                || evidence.failure() instanceof StoneSemanticSearchLimitExceededException || evidence.failure() instanceof ModelCapacityExceededException
                || evidence.failure() instanceof ModelTokenBudgetExceededException)) throw new StoneChatUnavailableException(evidence.failure());
        models.admission().probe(); ragControls.probe();
        redis.probe("chat:completion:" + UUID.randomUUID() + ":");
        components.sessions().authorize(sessionId, conversationId, false);
        components.history().findById(sessionId, conversationId);
        if (!Instant.now().isBefore(evidence.deadline()) || evidence.controlEpoch() != tracking.status().get().controlEpoch()
                || !providerFailure && !tracking.status().get().ready()) throw new StoneChatUnavailableException();
    }
    public void assertReady() { if (!tracking.status().get().ready()) throw new StoneChatUnavailableException(); }
    public <T> T execute(Supplier<T> work) {
        try { return work.get(); }
        catch (StoneChatStreamCapacityExceededException | StoneChatRateLimitedException | StoneChatConversationBusyException | StoneChatConversationNotFoundException
                | StoneChatOriginRejectedException | StoneChatTurnConflictException | ModelCapacityExceededException
                | ModelTokenBudgetExceededException | InvalidStoneExactSearchOutputException
                | InvalidStoneSemanticSearchOutputException | StoneSemanticSearchLimitExceededException
                | InvalidStoneRagOutputException | StoneRagAnswerLimitExceededException exception) { throw exception; }
        catch (RuntimeException exception) { invalidate(exception); throw new StoneChatUnavailableException(exception); }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { try { recover(); } catch (StoneChatUnavailableException ignored) { /* Chat readiness is independent of application liveness. */ } }
    public void ensureReady(boolean manualHistoryRecovery) {
        if (!tracking.status().get().ready() && manualHistoryRecovery) recover();
        assertReady();
    }
    public void recover() {
        if (!tracking.probing().compareAndSet(false, true)) throw new StoneChatUnavailableException();
        Status previous = tracking.status().get(); String prefix = "chat:probe:" + UUID.randomUUID() + ":";
        try {
            execute(() -> {
                if (models == null) throw new StoneChatUnavailableException();
                models.admission().probe(); models.provider().probe(); if (ragControls != null) ragControls.probe();
                redis.probe(prefix); components.sessions().probe(prefix); components.history().probe(prefix);
                components.limiter().probe(prefix); components.controls().leases().probe(prefix); serialization.probe();
                if (components.controls().context().select(List.of()) == null)
                    throw new StoneChatUnavailableException();
                return null;
            });
            if (!tracking.status().compareAndSet(previous, new Status(previous.generation(), previous.controlEpoch(), true)))
                throw new StoneChatUnavailableException();
        } finally { tracking.probing().set(false); }
    }
}
