package lab.stoneshelter.services;

import java.util.List;
import java.util.Set;
import java.time.Instant;
import lab.stoneshelter.shared.StoneExactSearchDecision;
import lab.stoneshelter.enums.StoneExactSearchAction;
import lab.stoneshelter.enums.StoneExactSearchReferenceKind;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class StoneExactSearchValidationService {
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    @Transactional(readOnly = true)
    public StoneExactSearchDecision validate(String output) {
        try {
            JsonNode root = jsonMapper.readTree(output);
            fields(root, Set.of("action", "criteria", "resetSearch", "reference", "questions", "reason"));
            if (!root.get("action").isString() || !root.get("resetSearch").isBoolean() || !root.get("reason").isString()) invalid();
            JsonNode criteria = root.get("criteria"); fields(criteria, Set.of("stoneTypes", "stoneSizes", "availability", "admissionDateRanges"));
            for (String name : List.of("stoneTypes", "stoneSizes", "availability", "admissionDateRanges", "questions")) {
                JsonNode array = "questions".equals(name) ? root.get(name) : criteria.get(name);
                if (!array.isArray()) invalid();
                if (!"admissionDateRanges".equals(name)) for (JsonNode value : array) if (!value.isString() || value.asString().isBlank()) invalid();
            }
            for (JsonNode range : criteria.get("admissionDateRanges")) {
                fields(range, Set.of("startInclusive", "endExclusive"));
                if (!range.get("startInclusive").isString() || !range.get("endExclusive").isString()
                        || !Instant.parse(range.get("startInclusive").asString()).isBefore(Instant.parse(range.get("endExclusive").asString()))) invalid();
            }
            JsonNode reference = root.get("reference");
            if (!reference.isNull()) {
                fields(reference, Set.of("kind", "ordinal"));
                if (!reference.get("kind").isString() || !(reference.get("ordinal").isNull()
                        || reference.get("ordinal").isIntegralNumber() && reference.get("ordinal").canConvertToInt())) invalid();
            }
            StoneExactSearchDecision decision = jsonMapper.readValue(output, StoneExactSearchDecision.class);
            if (decision.getReference() != null && (decision.getReference().kind() == StoneExactSearchReferenceKind.ORDINAL
                    ? decision.getReference().ordinal() == null || decision.getReference().ordinal() < 1
                    : decision.getReference().ordinal() != null)) invalid();
            if (decision.getAction() == StoneExactSearchAction.SEARCH && !decision.getQuestions().isEmpty()) invalid();
            if (decision.getAction() == StoneExactSearchAction.CLARIFY && decision.getQuestions().isEmpty()) invalid();
            return decision;
        } catch (RuntimeException exception) { throw new InvalidStoneExactSearchOutputException(exception); }
    }
    private void fields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(fields)) invalid();
    }
    private void invalid() { throw new InvalidStoneExactSearchOutputException(); }
}
