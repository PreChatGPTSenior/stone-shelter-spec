package lab.stoneshelter.services;

import java.util.UUID;
import java.util.function.Supplier;
import lab.stoneshelter.entities.StoneChatAdmittedTurnEntity;
import lab.stoneshelter.entities.StoneChatStreamEntity;
import lab.stoneshelter.entities.StoneChatConversationEntity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneRagPreparedTurnEntity;
import lab.stoneshelter.mappers.dtos.StoneChatConversationEntityToStoneChatHistoryResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatTurnEntityToStoneChatMessageResponseMapper;
import lab.stoneshelter.mappers.entities.StoneChatMessageRequestToStoneChatTurnEntityMapper;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.shared.StoneChatHistoryResponse;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import lab.stoneshelter.shared.StoneChatMessageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneChatbotService {
    private record Components(StoneChatConversationRepository history, StoneChatConversationSerializationService serialization,
                              StoneChatAdmissionService admission, StoneChatAvailabilityService availability) {}
    private record Mapping(StoneChatMessageRequestToStoneChatTurnEntityMapper request, StoneChatTurnEntityToStoneChatMessageResponseMapper response,
                           StoneChatConversationEntityToStoneChatHistoryResponseMapper history, StoneChatContextSelector context) {}
    private record Execution(StoneRagConversationService search, Clock clock, Duration deadline) {}
    private Execution execution;
    @Autowired public void configureRagSearch(StoneRagConversationService search, Clock clock, StoneChatSettings settings) {
        execution = new Execution(search, clock, settings.deadline());
    }
    private final Components components;
    private final Mapping mapping;
    public StoneChatbotService(StoneChatConversationRepository history, StoneChatConversationSerializationService serialization,
            StoneChatAdmissionService admission, StoneChatAvailabilityService availability, StoneChatMessageRequestToStoneChatTurnEntityMapper requestMapper,
            StoneChatTurnEntityToStoneChatMessageResponseMapper responseMapper, StoneChatConversationEntityToStoneChatHistoryResponseMapper historyMapper,
            StoneChatContextSelector contextSelector) {
        components = new Components(history, serialization, admission, availability);
        mapping = new Mapping(requestMapper, responseMapper, historyMapper, contextSelector);
    }
    private record Admitted(StoneChatAdmittedTurnEntity turn, StoneChatConversationEntity conversation,
                            StoneChatConversationSerializationService.Guard guard, Runnable releaseTransport) {}
    private Admitted admit(StoneChatMessageRequest request, String sessionId, Supplier<Runnable> transport) {
        return components.availability().execute(() -> {
            components.admission().check(sessionId, request.conversationId(), true);
            StoneChatTurnEntity turn = mapping.request().toEntity(request);
            StoneChatTurnEntity replay = components.history().findCommitted(components.history().findById(sessionId, request.conversationId()), turn);
            Runnable release = transport.get();
            StoneChatConversationSerializationService.Guard guard = null;
            try {
                StoneChatAdmittedTurnEntity admitted = new StoneChatAdmittedTurnEntity(); admitted.setConversationId(request.conversationId());
                admitted.setSessionId(sessionId); admitted.setRequestTurn(turn); admitted.setReplayTurn(replay);
                admitted.setDeadline(execution.clock().instant().plus(execution.deadline()));
                if (replay != null) { components.availability().assertReady(); return new Admitted(admitted, null, null, release); }
                components.admission().claim(sessionId, request.conversationId());
                guard = components.serialization().acquire(sessionId, request.conversationId());
                components.availability().assertReady();
                StoneChatConversationEntity conversation = components.history().findById(sessionId, request.conversationId());
                admitted.setReplayTurn(components.history().findCommitted(conversation, turn));
                if (admitted.getReplayTurn() != null) { guard.close(); guard = null; return new Admitted(admitted, null, null, release); }
                if (mapping.context().select(conversation.turns()) == null) throw new StoneChatUnavailableException();
                guard.check(); components.availability().assertReady();
                return new Admitted(admitted, conversation, guard, release);
            } catch (RuntimeException exception) {
                try { if (guard != null) guard.close(); } finally { release.run(); }
                throw exception;
            }
        });
    }
    private StoneChatStreamEntity start(Admitted admitted) {
        StoneChatStreamEntity stream = new StoneChatStreamEntity(); stream.setAdmittedTurn(admitted.turn()); stream.setReleaseTransport(admitted.releaseTransport());
        if (admitted.turn().getReplayTurn() != null) { stream.confirm(admitted.turn().getReplayTurn()); return stream; }
        try {
            Thread.ofVirtual().name("stone-search-turn").start(() -> {
                try {
                    StoneChatTurnEntity saved = components.availability().execute(() -> {
                        StoneRagPreparedTurnEntity committed;
                        try (var guard = admitted.guard()) {
                            StoneRagPreparedTurnEntity completed = execution.search().complete(admitted.turn().getRequestTurn(), admitted.conversation().turns(), admitted.turn().getDeadline());
                            assertProcessingDeadline(admitted.turn()); guard.check(); components.availability().assertCompletion(completed, admitted.turn().getSessionId(), admitted.turn().getConversationId());
                            committed = new StoneRagPreparedTurnEntity(components.history().append(admitted.turn().getSessionId(), admitted.turn().getConversationId(), guard.token(), completed.turn()), completed.completionEvidence());
                            guard.check(); components.availability().assertCompletion(committed, admitted.turn().getSessionId(), admitted.turn().getConversationId());
                        }
                        components.availability().assertCompletion(committed, admitted.turn().getSessionId(), admitted.turn().getConversationId());
                        return committed.turn();
                    });
                    stream.confirm(saved);
                } catch (RuntimeException exception) { stream.fail(exception); }
            });
            return stream;
        } catch (RuntimeException exception) {
            try { admitted.guard().close(); } finally { stream.transportExited(); }
            throw exception;
        }
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneChatStreamEntity startMessage(StoneChatMessageRequest request, String sessionId, Supplier<Runnable> acquireTransport) {
        return start(admit(request, sessionId, acquireTransport));
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneChatMessageResponse sendMessage(StoneChatMessageRequest request, String sessionId, Runnable onCommitted) {
        StoneChatStreamEntity stream = start(admit(request, sessionId, () -> () -> {}));
        try {
            stream.getCompletion().get(Math.max(1, Duration.between(execution.clock().instant(), stream.getAdmittedTurn().getDeadline()).toNanos()), TimeUnit.NANOSECONDS);
            if (stream.getAdmittedTurn().getReplayTurn() == null) onCommitted.run();
            return mapping.response().toDto(savedTurn(stream));
        } catch (InterruptedException exception) {
            components.availability().invalidate(exception); Thread.currentThread().interrupt(); throw new StoneChatUnavailableException(exception);
        } catch (TimeoutException exception) { components.availability().invalidate(exception); throw new StoneChatUnavailableException(exception); }
        catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new StoneChatUnavailableException(exception);
        } finally { stream.transportExited(); }
    }
    private void assertProcessingDeadline(StoneChatAdmittedTurnEntity admitted) {
        if (!execution.clock().instant().isBefore(admitted.getDeadline())) throw new StoneChatUnavailableException();
    }
    private StoneChatTurnEntity savedTurn(StoneChatStreamEntity stream) {
        StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(stream.getAdmittedTurn().getRequestTurn().getTurnId());
        turn.setText(stream.getCompletion().getNow(null).text()); turn.setStones(stream.getCompletion().getNow(null).stones()); return turn;
    }
    @Transactional(readOnly = true)
    public StoneChatHistoryResponse findById(UUID conversationId, String sessionId) {
        return components.availability().execute(() -> {
            components.admission().authorize(sessionId, conversationId, false);
            StoneChatHistoryResponse response = mapping.history().toDto(components.history().findById(sessionId, conversationId));
            components.availability().assertReady(); return response;
        });
    }
}
