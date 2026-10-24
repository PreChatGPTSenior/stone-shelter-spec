package lab.stoneshelter.services;

import java.util.UUID;
import java.util.function.Consumer;
import lab.stoneshelter.entities.StoneChatSessionEntity;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneChatAdmissionService {
    private record Guards(StoneChatAvailabilityService availability, StoneChatOriginService origin, StoneChatClientAddressService address) {}
    private final Guards guards;
    private final StoneChatRateLimiterRepository limiter;
    private final StoneChatSessionRepository sessions;
    private final StoneChatSettings settings;
    public StoneChatAdmissionService(StoneChatAvailabilityService availability, StoneChatOriginService origin,
            StoneChatClientAddressService address, StoneChatRateLimiterRepository limiter, StoneChatSessionRepository sessions, StoneChatSettings settings) {
        guards = new Guards(availability, origin, address); this.limiter = limiter; this.sessions = sessions; this.settings = settings;
    }
    public void prepare(boolean send, String peer, String forwarded, String origin) {
        guards.availability().execute(() -> {
            guards.availability().ensureReady(!send);
            limiter.consume(send, false, guards.address().resolve(peer, forwarded));
            if (send) guards.origin().validate(origin); return null;
        });
    }
    public String bind(String cookie, Consumer<String> onCreated) {
        return guards.availability().execute(() -> {
            guards.availability().assertReady(); StoneChatSessionEntity session = sessions.findOrCreate(cookie);
            if (session.created()) onCreated.accept(session.id()); return session.id();
        });
    }
    public void authorize(String sessionId, UUID conversationId, boolean send) {
        guards.availability().assertReady(); limiter.consume(send, true, sessionId); sessions.authorize(sessionId, conversationId, send);
    }
    public void check(String sessionId, UUID conversationId, boolean send) {
        guards.availability().assertReady(); limiter.consume(send, true, sessionId); sessions.authorize(sessionId, conversationId, false);
    }
    public void claim(String sessionId, UUID conversationId) { sessions.authorize(sessionId, conversationId, true); }
    public long remainingRetentionSeconds(String sessionId) { return sessions.remainingRetentionMillis(sessionId) / 1000; }
    public long retentionSeconds() { return settings.ttl().toSeconds(); }
}
