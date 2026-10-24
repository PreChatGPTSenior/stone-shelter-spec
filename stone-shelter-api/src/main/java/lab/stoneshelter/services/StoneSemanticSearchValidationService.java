package lab.stoneshelter.services;

import java.util.HashSet;
import java.util.Set;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.shared.StoneSemanticSearchDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional
public class StoneSemanticSearchValidationService {
    private final StoneExactSearchValidationService exact;
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public StoneSemanticSearchValidationService(StoneExactSearchValidationService exact) { this.exact = exact; }
    @Transactional(readOnly = true)
    public StoneSemanticSearchDecision validate(String output) {
        try {
            JsonNode root = jsonMapper.readTree(output);
            fields(root, Set.of("action", "criteria", "resetSearch", "reference", "questions", "reason", "wishes"));
            ObjectNode exactNode = ((ObjectNode) root).deepCopy(); exactNode.remove("wishes");
            exact.validate(jsonMapper.writeValueAsString(exactNode));
            JsonNode wishes = root.get("wishes"); fields(wishes, Set.of("color", "use", "other"));
            Set<String> keys = new HashSet<>();
            for (String key : new String[]{"color", "use"}) {
                JsonNode value = wishes.get(key);
                if (!value.isNull()) { text(value); keys.add(key); }
            }
            if (!wishes.get("other").isArray()) invalid();
            for (JsonNode wish : wishes.get("other")) {
                fields(wish, Set.of("key", "text")); text(wish.get("text"));
                if (!wish.get("key").isString() || !wish.get("key").asString().matches("[a-z][a-z0-9_-]{0,31}")
                        || Set.of("color", "use").contains(wish.get("key").asString()) || !keys.add(wish.get("key").asString())) invalid();
            }
            return jsonMapper.readValue(output, StoneSemanticSearchDecision.class);
        } catch (RuntimeException exception) { throw new InvalidStoneSemanticSearchOutputException(exception); }
    }
    private void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(expected)) invalid();
    }
    private void text(JsonNode node) {
        if (!node.isString() || node.asString().isBlank() || Set.of("null", "none").contains(node.asString().strip().toLowerCase(java.util.Locale.ROOT))) invalid();
    }
    private void invalid() { throw new InvalidStoneSemanticSearchOutputException(); }
}
