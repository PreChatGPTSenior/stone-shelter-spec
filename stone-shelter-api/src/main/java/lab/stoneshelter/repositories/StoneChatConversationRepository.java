package lab.stoneshelter.repositories;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatConversationEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.exceptions.StoneChatTurnConflictException;
import lab.stoneshelter.services.StoneChatSettings;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class StoneChatConversationRepository {
    private static final String COMMIT_SCRIPT = """
                if redis.call('GET', KEYS[1]) ~= '1' or redis.call('GET', KEYS[2]) ~= ARGV[1]
                    then return 0 end
                local held = redis.call('GET', KEYS[3]); if not held then return 0 end
                local token, deadline = string.match(held, '^(.-)|(%d+)$')
                local now = redis.call('TIME'); local millis = now[1] * 1000 + math.floor(now[2] / 1000)
                if token ~= ARGV[2] or not deadline or tonumber(deadline) <= millis then return 0 end
                local previous = redis.call('GET', KEYS[4])
                if (previous or '') ~= ARGV[3] then return 0 end
                local ttl = tonumber(ARGV[5])
                if not ttl or ttl <= 0 then return 0 end
                redis.call('PSETEX', KEYS[4], ttl, ARGV[4])
                redis.call('PEXPIRE', KEYS[2], ttl)
                local sessionTtl = redis.call('PTTL', KEYS[1])
                if sessionTtl < ttl then redis.call('PEXPIRE', KEYS[1], ttl) end
                return 1
                """;
    private final StoneChatRedisRepository redis;
    private final StoneChatSettings settings;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    public StoneChatConversationRepository(StoneChatRedisRepository redis, StoneChatSettings settings) {
        this.redis = redis; this.settings = settings;
    }
    public StoneChatConversationEntity findById(String sessionId, UUID conversationId) {
        return decode(redis.get(key(sessionId, conversationId)), conversationId);
    }
    private StoneChatConversationEntity decode(String value, UUID conversationId) {
        if (value == null) return new StoneChatConversationEntity(1, conversationId, List.of());
        try {
            StoneChatConversationEntity conversation = jsonMapper.readValue(value, StoneChatConversationEntity.class);
            if (conversation.version() != 1 || !conversationId.equals(conversation.conversationId()) || conversation.turns() == null)
                throw new IllegalArgumentException("Invalid conversation schema");
            HashSet<UUID> turnIds = new HashSet<>();
            for (StoneChatTurnEntity turn : conversation.turns()) {
                if (turn == null || turn.getTurnId() == null || !turnIds.add(turn.getTurnId()) || turn.getMessage() == null
                        || turn.getText() == null || turn.getStones() == null || turn.getStones().stream()
                        .anyMatch(stone -> stone == null || stone.id() <= 0 || stone.name() == null))
                    throw new IllegalArgumentException("Invalid completed turn");
                if (turn.getExactSearchCriteria() != null) {
                    var criteria = turn.getExactSearchCriteria();
                    if (criteria.getStoneId() != null && criteria.getStoneId() <= 0 || criteria.getAdmissionDateRanges().stream()
                            .anyMatch(range -> range == null || range.startInclusive() == null || range.endExclusive() == null
                                    || !range.startInclusive().isBefore(range.endExclusive())))
                        throw new IllegalArgumentException("Invalid stored exact-search metadata");
                }
                if (turn.getRagState() != null) {
                    var state = turn.getRagState();
                    if (state.mode() == null || state.completion() == null || state.actualSelection()
                            && (state.mode() != lab.stoneshelter.enums.StoneRagMode.SELECTION || turn.getStones().isEmpty()
                                || !turn.getTurnId().equals(state.selectionReferenceTurnId())))
                        throw new IllegalArgumentException("Invalid stored answer-route metadata");
                }

                if (turn.getSemanticSearchState() != null) {
                    var state = turn.getSemanticSearchState();
                    if (state.getExact() == null || state.getWishes() == null || state.getMode() == null
                            || state.getWishes().stream().anyMatch(wish -> wish == null)
                            || state.getWishes().stream().map(lab.stoneshelter.criteria.StoneSemanticWish::key).distinct().count() != state.getWishes().size()
                            || !Objects.equals(jsonMapper.writeValueAsString(state.getExact()), jsonMapper.writeValueAsString(turn.getExactSearchCriteria())))
                        throw new IllegalArgumentException("Invalid stored semantic-search state");
                }
                if (turn.getSemanticCandidates() != null && (turn.getSemanticSearchState() == null
                        || turn.getSemanticCandidates().size() != turn.getStones().size()))
                    throw new IllegalArgumentException("Invalid stored semantic candidate proof");
                if (turn.getSemanticCandidates() != null) for (int index = 0; index < turn.getSemanticCandidates().size(); index++) {
                    var candidate = turn.getSemanticCandidates().get(index);
                    if (candidate == null || candidate.id() != turn.getStones().get(index).id()
                            || candidate.evidence().stream().anyMatch(proof -> proof == null || proof.verdict() == null
                                    || proof.key() == null || proof.sourceId() == null))
                        throw new IllegalArgumentException("Invalid stored semantic evidence");
                }

            }
            return conversation;
        } catch (RuntimeException exception) { throw redis.unavailable(exception); }
    }
    public StoneChatTurnEntity findCommitted(StoneChatConversationEntity conversation, StoneChatTurnEntity request) {
        for (StoneChatTurnEntity committed : conversation.turns()) if (committed.getTurnId().equals(request.getTurnId())) {
            if (!Objects.equals(committed.getMessage(), request.getMessage()) || !Objects.equals(committed.getContext(), request.getContext()))
                throw new StoneChatTurnConflictException();
            return committed;
        }
        return null;
    }
    public StoneChatTurnEntity append(String sessionId, UUID conversationId, String leaseToken, StoneChatTurnEntity turn) {
        String historyKey = key(sessionId, conversationId);
        String original = redis.get(historyKey);
        StoneChatConversationEntity conversation = decode(original, conversationId);
        StoneChatTurnEntity committed = findCommitted(conversation, turn);
        if (committed != null) return committed;
        List<StoneChatTurnEntity> turns = new ArrayList<>(conversation.turns()); turns.add(turn);
        String value;
        try { value = jsonMapper.writeValueAsString(new StoneChatConversationEntity(1, conversationId, List.copyOf(turns))); }
        catch (RuntimeException exception) { throw redis.unavailable(exception); }
        long result = redis.eval(COMMIT_SCRIPT, List.of(StoneChatSessionRepository.sessionKey(sessionId), StoneChatSessionRepository.ownerKey(conversationId),
                StoneChatActiveTurnRepository.key(sessionId, conversationId), historyKey), sessionId, leaseToken,
                original == null ? "" : original, value, Long.toString(settings.ttl().toMillis()));
        if (result != 1) throw redis.unavailable(new IllegalStateException("Completed turn ownership or prior state changed"));
        return turn;
    }
    public void probe(String prefix) {
        List<String> keys = List.of(prefix + "commit-session", prefix + "commit-owner", prefix + "commit-lease", prefix + "history");
        UUID id = UUID.randomUUID(); String token = UUID.randomUUID().toString();
        try {
            redis.eval("redis.call('PSETEX', KEYS[1], 10000, '1'); redis.call('PSETEX', KEYS[2], 10000, ARGV[1]); "
                    + "local now = redis.call('TIME'); local deadline = now[1] * 1000 + math.floor(now[2] / 1000) + 10000; "
                    + "redis.call('PSETEX', KEYS[3], 10000, ARGV[2] .. '|' .. deadline); return 1", keys.subList(0, 3), prefix, token);
            StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage("probe");
            turn.setText("probe"); turn.setStones(List.of());
            String value = jsonMapper.writeValueAsString(new StoneChatConversationEntity(1, id, List.of(turn)));
            if (redis.eval(COMMIT_SCRIPT, keys, prefix, token, "", value, "10000") != 1
                    || decode(redis.get(keys.getLast()), id).turns().size() != 1)
                throw redis.unavailable(new IllegalStateException("Completed turn probe failed"));
        } finally { redis.delete(keys); }
    }
    public static String key(String sessionId, UUID conversationId) { return "chat:history:" + sessionId + ":" + conversationId; }
}
