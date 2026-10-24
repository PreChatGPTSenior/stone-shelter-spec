package lab.stoneshelter;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatContextEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.exceptions.StoneChatTurnConflictException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatConversationRepositoryTest extends StoneChatRedisTestSupport {
    @Test
    void atomicPairPreservesExactPayloadAndReplayDoesNotRefreshWhileStaleLeaseCannotCommit() {
        environment.withProperty("CHAT_MEMORY_TTL", "PT10S");
        StoneChatSessionRepository sessions = new StoneChatSessionRepository(redis, settings);
        String session = sessions.findOrCreate(null).id(); UUID conversation = UUID.randomUUID();
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), StoneChatSessionRepository.ownerKey(conversation),
                StoneChatActiveTurnRepository.key(session, conversation), StoneChatConversationRepository.key(session, conversation)));
        sessions.authorize(session, conversation, true);
        StoneChatActiveTurnRepository active = new StoneChatActiveTurnRepository(redis, settings);
        assertThat(active.acquire(session, conversation, "token")).isTrue();
        StoneChatConversationRepository repository = new StoneChatConversationRepository(redis, settings);
        StoneChatTurnEntity turn = turn();
        repository.append(session, conversation, "token", turn);
        String key = StoneChatConversationRepository.key(session, conversation);
        redis.eval("return redis.call('PEXPIRE', KEYS[1], 1000)", List.of(key));
        assertThat(repository.append(session, conversation, "wrong", turn).getText()).isEqualTo(turn.getText());
        assertThat(repository.findById(session, conversation).turns()).hasSize(1);
        assertThat(repository.findById(session, conversation).turns().getFirst().getContext()).isEqualTo(turn.getContext());
        assertThat(redis.<Long>execute(commands -> commands.pttl(StoneChatRedisRepository.bytes(key)))).isBetween(1L, 1000L);
        turn.setMessage("changed");
        assertThatThrownBy(() -> repository.append(session, conversation, "token", turn)).isInstanceOf(StoneChatTurnConflictException.class);
        StoneChatTurnEntity newTurn = turn();
        assertThatThrownBy(() -> repository.append(session, conversation, "wrong", newTurn)).isInstanceOf(StoneChatUnavailableException.class);
        assertThat(repository.findById(session, conversation).turns()).hasSize(1);
        repository.append(session, conversation, "token", newTurn);
        assertThat(repository.findById(session, conversation).turns()).hasSize(2);
        assertThat(redis.<Long>execute(commands -> commands.pttl(StoneChatRedisRepository.bytes(key)))).isBetween(9500L, 10000L);
    }
    @Test
    void unreadableHistoryIsNeverEmpty() {
        String session = "session"; UUID conversation = UUID.randomUUID(); String key = StoneChatConversationRepository.key(session, conversation); keys.add(key);
        redis.eval("redis.call('PSETEX', KEYS[1], 10000, '{broken'); return 1", List.of(key));
        assertThatThrownBy(() -> new StoneChatConversationRepository(redis, settings).findById(session, conversation))
                .isInstanceOf(StoneChatUnavailableException.class);
    }
    @Test
    void lostCommitAcknowledgementLeavesOneAuthoritativelyRecoverablePair() {
        StoneChatSessionRepository stoneChatSessionRepository = new StoneChatSessionRepository(redis, settings);
        String sessionId = stoneChatSessionRepository.findOrCreate(null).id();
        UUID conversationId = UUID.randomUUID();
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(sessionId), StoneChatSessionRepository.ownerKey(conversationId),
                StoneChatActiveTurnRepository.key(sessionId, conversationId), StoneChatConversationRepository.key(sessionId, conversationId)));
        stoneChatSessionRepository.authorize(sessionId, conversationId, true);
        new StoneChatActiveTurnRepository(redis, settings).acquire(sessionId, conversationId, "token");
        StoneChatRedisRepository stoneChatRedisRepository = spy(redis);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (invocation.<String>getArgument(0).contains("local previous ="))
                throw stoneChatRedisRepository.unavailable(new IllegalStateException("Commit acknowledgement lost"));
            return result;
        }).when(stoneChatRedisRepository).eval(anyString(), any(), any(String[].class));
        StoneChatConversationRepository stoneChatConversationRepository = new StoneChatConversationRepository(stoneChatRedisRepository, settings);
        StoneChatTurnEntity stoneChatTurnEntity = turn();
        assertThatThrownBy(() -> stoneChatConversationRepository.append(sessionId, conversationId, "token", stoneChatTurnEntity))
                .isInstanceOf(StoneChatUnavailableException.class);
        assertThat(stoneChatConversationRepository.findById(sessionId, conversationId).turns()).hasSize(1);
        assertThat(stoneChatConversationRepository.append(sessionId, conversationId, "token", stoneChatTurnEntity).getText())
                .isEqualTo(stoneChatTurnEntity.getText());
        assertThat(stoneChatConversationRepository.findById(sessionId, conversationId).turns()).hasSize(1);
    }
    @Test
    void anExpiredProcessingDeadlineCannotCommitEvenWhileTheLeaseStillExists() {
        StoneChatSessionRepository sessions = new StoneChatSessionRepository(redis, settings);
        String session = sessions.findOrCreate(null).id(); UUID conversation = UUID.randomUUID();
        String active = StoneChatActiveTurnRepository.key(session, conversation);
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), StoneChatSessionRepository.ownerKey(conversation),
                active, StoneChatConversationRepository.key(session, conversation)));
        sessions.authorize(session, conversation, true);
        redis.eval("redis.call('PSETEX', KEYS[1], 10000, 'token|1'); return 1", List.of(active));
        StoneChatConversationRepository repository = new StoneChatConversationRepository(redis, settings);
        assertThatThrownBy(() -> repository.append(session, conversation, "token", turn())).isInstanceOf(StoneChatUnavailableException.class);
        assertThat(repository.findById(session, conversation).turns()).isEmpty();
    }
    @Test
    void healthyExpiryIsAuthoritativeAbsenceAndFunctionalProbeUsesOnlyItsOwnKeys() throws Exception {
        String session = new StoneChatSessionRepository(redis, settings).findOrCreate(null).id(); UUID conversation = UUID.randomUUID();
        keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), StoneChatSessionRepository.ownerKey(conversation),
                StoneChatActiveTurnRepository.key(session, conversation), StoneChatConversationRepository.key(session, conversation)));
        new StoneChatSessionRepository(redis, settings).authorize(session, conversation, true);
        new StoneChatActiveTurnRepository(redis, settings).acquire(session, conversation, "token");
        StoneChatConversationRepository repository = new StoneChatConversationRepository(redis, settings);
        repository.append(session, conversation, "token", turn());
        redis.eval("return redis.call('PEXPIRE', KEYS[1], 100)", List.of(StoneChatConversationRepository.key(session, conversation)));
        Thread.sleep(180);
        assertThat(repository.findById(session, conversation).turns()).isEmpty();
        String prefix = key() + ":"; repository.probe(prefix);
        for (String suffix : List.of("commit-session", "commit-owner", "commit-lease", "history"))
            assertThat(redis.get(prefix + suffix)).isNull();
    }
    private static StoneChatTurnEntity turn() {
        StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage("  exact  ");
        turn.setContext(new StoneChatContextEntity(7L)); turn.setText("answer");
        turn.setStones(List.of(new StoneChatStoneEntity(3, "second"), new StoneChatStoneEntity(1, "first"))); return turn;
    }
}
