package lab.stoneshelter;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StoneChatActiveTurnRepositoryTest extends StoneChatRedisTestSupport {
    @Test
    void staleOwnerCannotRenewOrDeleteNewLease() {
        String sessionId = new StoneChatSessionRepository(redis, settings).findOrCreate(null).id();
        UUID conversationId = UUID.randomUUID();
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(sessionId), StoneChatSessionRepository.ownerKey(conversationId),
                StoneChatActiveTurnRepository.key(sessionId, conversationId)));
        new StoneChatSessionRepository(redis, settings).authorize(sessionId, conversationId, true);
        StoneChatActiveTurnRepository repository = new StoneChatActiveTurnRepository(redis, settings);
        assertThat(repository.acquire(sessionId, conversationId, "old")).isTrue();
        assertThat(repository.acquire(sessionId, conversationId, "new")).isFalse();
        repository.release(StoneChatActiveTurnRepository.key(sessionId, conversationId), "old");
        assertThat(repository.acquire(sessionId, conversationId, "new")).isTrue();
        repository.release(StoneChatActiveTurnRepository.key(sessionId, conversationId), "old");
        assertThat(repository.renew(StoneChatActiveTurnRepository.key(sessionId, conversationId), "old")).isFalse();
        assertThat(redis.get(StoneChatActiveTurnRepository.key(sessionId, conversationId))).startsWith("new|");
    }
}
