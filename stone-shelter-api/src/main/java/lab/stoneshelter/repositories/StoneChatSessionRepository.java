package lab.stoneshelter.repositories;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatSessionEntity;
import lab.stoneshelter.exceptions.StoneChatConversationNotFoundException;
import lab.stoneshelter.services.StoneChatSettings;
import org.springframework.stereotype.Repository;

@Repository
public class StoneChatSessionRepository {
    private final StoneChatRedisRepository redis;
    private final StoneChatSettings settings;
    private final SecureRandom secureRandom = new SecureRandom();
    public StoneChatSessionRepository(StoneChatRedisRepository redis, StoneChatSettings settings) {
        this.redis = redis; this.settings = settings;
    }
    public StoneChatSessionEntity findOrCreate(String cookie) {
        if (cookie != null && cookie.matches("[A-Za-z0-9_-]{43}")) {
            String value = redis.get(sessionKey(cookie));
            if (value != null) {
                if (!"1".equals(value)) throw redis.unavailable(new IllegalStateException("Unreadable session"));
                long ttl = redis.execute(commands -> commands.pttl(StoneChatRedisRepository.bytes(sessionKey(cookie))));
                if (ttl > 0) return new StoneChatSessionEntity(cookie, false, ttl);
                if (ttl != -2) throw redis.unavailable(new IllegalStateException("Invalid session retention"));
            }
        }
        byte[] random = new byte[32]; secureRandom.nextBytes(random);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        if (redis.eval("return redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) and 1 or 0",
                List.of(sessionKey(id)), Long.toString(settings.ttl().toMillis())) != 1)
            throw redis.unavailable(new IllegalStateException("Session allocation collision"));
        return new StoneChatSessionEntity(id, true, settings.ttl().toMillis());
    }
    public void authorize(String sessionId, UUID conversationId, boolean claim) {
        String owner = redis.get(ownerKey(conversationId));
        if (owner != null && !owner.matches("[A-Za-z0-9_-]{43}"))
            throw redis.unavailable(new IllegalStateException("Unreadable ownership"));
        if (owner != null && !sessionId.equals(owner)) throw new StoneChatConversationNotFoundException();
        if (claim) {
            long result = redis.eval("""
                    if redis.call('GET', KEYS[1]) ~= '1' then return -1 end
                    local owner = redis.call('GET', KEYS[2])
                    if owner and owner ~= ARGV[1] then return 0 end
                    if not owner then redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2]) end
                    return 1
                    """, List.of(sessionKey(sessionId), ownerKey(conversationId)), sessionId, Long.toString(settings.ttl().toMillis()));
            if (result == 0) throw new StoneChatConversationNotFoundException();
            if (result != 1) throw redis.unavailable(new IllegalStateException("Session lost during authorization"));
        }
    }
    public long remainingRetentionMillis(String sessionId) {
        long ttl = redis.execute(commands -> commands.pttl(StoneChatRedisRepository.bytes(sessionKey(sessionId))));
        if (ttl <= 0) throw redis.unavailable(new IllegalStateException("Session lost before cookie synchronization"));
        return ttl;
    }
    public void probe(String prefix) {
        String session = prefix + "session"; String owner = prefix + "owner";
        try {
            if (redis.eval("redis.call('PSETEX', KEYS[1], 10000, '1'); redis.call('PSETEX', KEYS[2], 10000, ARGV[1]); return 1",
                    List.of(session, owner), prefix) != 1 || !"1".equals(redis.get(session)) || !prefix.equals(redis.get(owner)))
                throw redis.unavailable(new IllegalStateException("Session probe failed"));
        } finally { redis.delete(List.of(session, owner)); }
    }
    public static String sessionKey(String id) { return "chat:session:" + id; }
    public static String ownerKey(UUID id) { return "chat:owner:" + id; }
}
