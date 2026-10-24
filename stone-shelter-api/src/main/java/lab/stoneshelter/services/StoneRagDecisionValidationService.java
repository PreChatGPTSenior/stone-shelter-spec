package lab.stoneshelter.services;

import java.util.Set;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.mappers.dtos.StoneRagDecisionToStoneSemanticSearchDecisionMapper;
import lab.stoneshelter.shared.StoneRagDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class StoneRagDecisionValidationService {
    private final StoneSemanticSearchValidationService semantic;
    private final StoneRagDecisionToStoneSemanticSearchDecisionMapper mapping;
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public StoneRagDecisionValidationService(StoneSemanticSearchValidationService semantic, StoneRagDecisionToStoneSemanticSearchDecisionMapper mapping) {
        this.semantic = semantic; this.mapping = mapping;
    }
    public StoneRagDecision validate(String output) {
        try {
            JsonNode root = jsonMapper.readTree(output);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("mode", "criteria", "resetSearch", "reference", "questions", "reason", "wishes", "topic"))
                    || !root.get("mode").isString() || !root.get("topic").isString()) throw new InvalidStoneRagOutputException();
            StoneRagDecision decision = jsonMapper.readValue(output, StoneRagDecision.class);
            semantic.validate(jsonMapper.writeValueAsString(mapping.toDto(decision)));
            if (decision.getMode() == StoneRagMode.KNOWLEDGE && (decision.getTopic().isBlank() || !decision.getQuestions().isEmpty()
                    || decision.getWishes().color() != null || decision.getWishes().use() != null || !decision.getWishes().other().isEmpty()))
                throw new InvalidStoneRagOutputException();
            return decision;
        } catch (InvalidStoneRagOutputException exception) { throw exception; }
        catch (RuntimeException exception) { throw new InvalidStoneRagOutputException(exception); }
    }
}
