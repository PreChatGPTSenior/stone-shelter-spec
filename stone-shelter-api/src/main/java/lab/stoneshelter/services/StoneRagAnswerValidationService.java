package lab.stoneshelter.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lab.stoneshelter.entities.StoneRagGeneratedAnswerEntity;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class StoneRagAnswerValidationService {
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public StoneRagGeneratedAnswerEntity validate(String output, StoneRagGenerationRequest request) {
        try {
            JsonNode root = jsonMapper.readTree(output); fields(root, Set.of("introduction", "stones", "knowledge"));
            if (!root.get("introduction").isString() || !root.get("stones").isArray()) invalid();
            List<StoneRagGeneratedAnswerEntity.Explanation> stones = new ArrayList<>();
            for (JsonNode stone : root.get("stones")) {
                fields(stone, Set.of("id", "explanation"));
                if (!stone.get("id").isIntegralNumber() || !stone.get("id").canConvertToLong() || !stone.get("explanation").isString() || stone.get("explanation").asString().isBlank()) invalid();
                stones.add(new StoneRagGeneratedAnswerEntity.Explanation(stone.get("id").asLong(), stone.get("explanation").asString()));
            }
            if (request.getMode() == StoneRagMode.SELECTION) {
                if (root.get("introduction").asString().isBlank() || !root.get("knowledge").isNull() || stones.size() != request.getCandidates().size()) invalid();
                for (int index = 0; index < stones.size(); index++) {
                    if (stones.get(index).id() != request.getCandidates().get(index).getId()
                            || stones.get(index).explanation().strip().equalsIgnoreCase(request.getCandidates().get(index).getName())) invalid();
                }
            } else if (request.getMode() != StoneRagMode.KNOWLEDGE || !stones.isEmpty() || !root.get("knowledge").isString() || root.get("knowledge").asString().isBlank()
                    || root.get("knowledge").asString().strip().matches("(?is)(?:stones|knowledge)\\s*=.*")) invalid();
            String knowledge = root.get("knowledge").isNull() ? null : root.get("knowledge").asString();
            checkVisibility(root.get("introduction").asString(), request); for (var stone : stones) checkVisibility(stone.explanation(), request);
            if (knowledge != null) checkVisibility(knowledge, request);
            return new StoneRagGeneratedAnswerEntity(root.get("introduction").asString(), stones, knowledge);
        } catch (InvalidStoneRagOutputException exception) { throw exception; }
        catch (RuntimeException exception) { throw new InvalidStoneRagOutputException(exception); }
    }
    private void checkVisibility(String prose, StoneRagGenerationRequest request) {
        if (hasVisibleProvenance(prose, request)) invalid();
    }
    public static boolean hasVisibleProvenance(String prose, StoneRagGenerationRequest request) {
        return Pattern.compile("(?i)https?://|\\[[0-9]+\\]|\\baccording to\\b|\\b(?:the|its) (?:card|biography|source|document|encyclopedia)\\b|\\b(?:card|biography|source|document|encyclopedia) (?:says|states|shows|describes)\\b")
                .matcher(prose).find() || request.getSources().stream().anyMatch(source -> prose.contains(source.id()));
    }
    private void fields(JsonNode node, Set<String> expected) { if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(expected)) invalid(); }
    private void invalid() { throw new InvalidStoneRagOutputException(); }
}
