package lab.stoneshelter.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lab.stoneshelter.entities.StoneSemanticEvidenceResultEntity;
import lab.stoneshelter.enums.StoneSemanticEvidenceVerdict;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.shared.StoneSemanticEvidenceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class StoneSemanticEvidenceValidationService {
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    @Transactional(readOnly = true)
    public StoneSemanticEvidenceResultEntity validate(String output, StoneSemanticEvidenceRequest stoneSemanticEvidenceRequest) {
        try {
            JsonNode root = jsonMapper.readTree(output);
            fields(root, Set.of("judgements"));
            if (!root.get("judgements").isArray()) invalid();
            Set<String> expected = new HashSet<>();
            for (int index = 0; index < stoneSemanticEvidenceRequest.sources().size(); index++) {
                for (var wish : stoneSemanticEvidenceRequest.wishes()) expected.add(index + ":" + wish.key());
            }
            List<StoneSemanticEvidenceResultEntity.Judgement> judgements = new ArrayList<>();
            for (JsonNode value : root.get("judgements")) {
                fields(value, Set.of("key", "sourceId", "verdict", "quote"));
                if (!value.get("key").isString() || !value.get("sourceId").isString() || !value.get("verdict").isString()) invalid();
                String sourceId = value.get("sourceId").asString();
                if (!sourceId.matches("s(?:0|[1-9][0-9]*)")) invalid();
                int index = Integer.parseInt(sourceId.substring(1));
                if (index >= stoneSemanticEvidenceRequest.sources().size() || !expected.remove(index + ":" + value.get("key").asString())) invalid();
                var source = stoneSemanticEvidenceRequest.sources().get(index);
                StoneSemanticEvidenceVerdict verdict = StoneSemanticEvidenceVerdict.valueOf(value.get("verdict").asString());
                String quote = null;
                if (verdict == StoneSemanticEvidenceVerdict.UNKNOWN) {
                    if (!value.get("quote").isNull()) invalid();
                } else {
                    if (!value.get("quote").isString()) invalid();
                    quote = value.get("quote").asString();
                    if (quote.isBlank() || quote.length() > OllamaSemanticEvidenceProvider.MAX_QUOTE_LENGTH || !source.text().contains(quote)) {
                        throw new InvalidStoneSemanticSearchOutputException("Invalid quote for " + sourceId + "/" + value.get("key").asString()
                                + ". Quote only this source's text, never the wish or instructions. Absent support is UNKNOWN/null.");
                    }
                }
                judgements.add(new StoneSemanticEvidenceResultEntity.Judgement(value.get("key").asString(), source.id(), verdict, quote));
            }
            if (!expected.isEmpty()) invalid();
            return new StoneSemanticEvidenceResultEntity(judgements);
        } catch (InvalidStoneSemanticSearchOutputException exception) { throw exception; }
        catch (RuntimeException exception) { throw new InvalidStoneSemanticSearchOutputException(exception); }
    }

    private void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(expected)) invalid();
    }
    private void invalid() { throw new InvalidStoneSemanticSearchOutputException(); }
}
