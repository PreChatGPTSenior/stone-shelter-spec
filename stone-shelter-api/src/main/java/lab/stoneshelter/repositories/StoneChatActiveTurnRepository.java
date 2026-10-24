package lab.stoneshelter.repositories;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.services.StoneChatSettings;
import org.springframework.stereotype.Repository;

@Repository
public class StoneChatActiveTurnRepository {
    private final StoneChatRedisRepository redis;
    private final StoneChatSettings settings;
    public StoneChatActiveTurnRepository(StoneChatRedisRepository redis, StoneChatSettings settings) {
        this.redis = redis; this.settings = settings;
    }
    public boolean acquire(String sessionId, UUID conversationId, String token) {
        long result = redis.eval("""
                if redis.call('GET', KEYS[1]) ~= '1' or redis.call('GET', KEYS[2]) ~= ARGV[1] then return -1 end
                local now = redis.call('TIME'); local deadline = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[4])
                return redis.call('SET', KEYS[3], ARGV[2] .. '|' .. deadline, 'NX', 'PX', ARGV[3]) and 1 or 0
                """, List.of(StoneChatSessionRepository.sessionKey(sessionId), StoneChatSessionRepository.ownerKey(conversationId),
                key(sessionId, conversationId)), sessionId, token, Long.toString(settings.lease().toMillis()), Long.toString(settings.deadline().toMillis()));
        if (result < 0) throw unavailable(new IllegalStateException("Lost session or ownership"));
        return result == 1;
    }
    public boolean renew(String key, String token) {
        return redis.eval("""
                local held = redis.call('GET', KEYS[1]); if not held then return 0 end
                local token, deadline = string.match(held, '^(.-)|(%d+)$')
                local now = redis.call('TIME'); local millis = now[1] * 1000 + math.floor(now[2] / 1000)
                if token ~= ARGV[1] or not deadline or tonumber(deadline) <= millis then return 0 end
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
                """,
                List.of(key), token, Long.toString(settings.lease().toMillis())) == 1;
    }
    public void release(String key, String token) {
        redis.eval("local held = redis.call('GET', KEYS[1]); if not held or string.match(held, '^(.-)|%d+$') ~= ARGV[1] then return 0 end; return redis.call('DEL', KEYS[1])", List.of(key), token);
    }
    public StoneChatUnavailableException unavailable(Throwable failure) { return redis.unavailable(failure); }
    public void probe(String prefix) {
        String key = prefix + "lease";
        String token = UUID.randomUUID().toString();
        try {
            if (redis.eval("local now = redis.call('TIME'); local deadline = now[1] * 1000 + math.floor(now[2] / 1000) + 10000; "
                    + "return redis.call('SET', KEYS[1], ARGV[1] .. '|' .. deadline, 'NX', 'PX', 10000) and 1 or 0", List.of(key), token) != 1
                    || !renew(key, token)) throw unavailable(new IllegalStateException("Lease probe failed"));
            release(key, token);
            if (redis.get(key) != null) throw unavailable(new IllegalStateException("Lease release probe failed"));
        } finally { release(key, token); }
    }
    public static String key(String sessionId, UUID conversationId) { return "chat:active:" + sessionId + ":" + conversationId; }
}
