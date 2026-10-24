package lab.stoneshelter;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatSessionEntity;
import lab.stoneshelter.exceptions.StoneChatConversationNotFoundException;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatSessionRepositoryTest extends StoneChatRedisTestSupport {
    @Test
    void serverIssuedIdentitySurvivesReadsWithoutRefreshingOrTransferringEmptyOwnership() {
        StoneChatSessionRepository repository = new StoneChatSessionRepository(redis, settings);
        StoneChatSessionEntity session = repository.findOrCreate(null);
        keys.add(StoneChatSessionRepository.sessionKey(session.id()));
        UUID conversationId = UUID.randomUUID(); keys.add(StoneChatSessionRepository.ownerKey(conversationId));
        repository.authorize(session.id(), conversationId, true);
        assertThat(repository.findOrCreate(session.id()).created()).isFalse();
        StoneChatSessionEntity other = repository.findOrCreate(null);
        keys.add(StoneChatSessionRepository.sessionKey(other.id()));
        assertThatThrownBy(() -> repository.authorize(other.id(), conversationId, false))
                .isInstanceOf(StoneChatConversationNotFoundException.class);
        assertThatThrownBy(() -> repository.authorize(other.id(), conversationId, true))
                .isInstanceOf(StoneChatConversationNotFoundException.class);
        redis.eval("return redis.call('PEXPIRE', KEYS[1], 1000)", List.of(StoneChatSessionRepository.sessionKey(session.id())));
        repository.findOrCreate(session.id());
        assertThat(redis.<Long>execute(commands -> commands.pttl(StoneChatRedisRepository.bytes(StoneChatSessionRepository.sessionKey(session.id())))))
                .isBetween(1L, 1000L);
    }
}
