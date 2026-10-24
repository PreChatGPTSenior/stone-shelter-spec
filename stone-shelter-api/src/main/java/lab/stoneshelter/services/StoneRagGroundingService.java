package lab.stoneshelter.services;

import tools.jackson.core.JacksonException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.stoneshelter.entities.StoneRagGeneratedAnswerEntity;
import lab.stoneshelter.entities.StoneRagGroundingResultEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneRagGenerationRequestToStoneRagGroundingRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import lab.stoneshelter.shared.StoneRagGroundingRequest;
import lab.stoneshelter.shared.StoneRagGroundingResponse;
import lab.stoneshelter.shared.StoneRagSourceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneRagGroundingService {
    private record Mapping(StoneRagGenerationRequestToStoneRagGroundingRequestMapper request,
            StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper response) {}
    private record Components(StoneRagGroundingProvider provider, OllamaRagGroundingProvider packing,
            Mapping mapping, StoneSemanticSearchSettings settings) {}
    private final Components components;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public StoneRagGroundingService(StoneRagGroundingProvider provider, OllamaRagGroundingProvider packing,
            StoneRagGenerationRequestToStoneRagGroundingRequestMapper requestMapper, StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper responseMapper,
            StoneSemanticSearchSettings settings, Clock clock) {
        components = new Components(provider, packing, new Mapping(requestMapper, responseMapper), settings); this.clock = clock;
    }
    public StoneRagGroundingResponse check(StoneRagGenerationRequest facts, StoneRagGeneratedAnswerEntity draft,
            StoneSemanticSearchExecutionEntity execution) {
        return components.mapping().response().toDto(prepare(facts, draft, execution));
    }
    public StoneRagGroundingResultEntity prepare(StoneRagGenerationRequest facts, StoneRagGeneratedAnswerEntity draft,
            StoneSemanticSearchExecutionEntity execution) {
        // The caller will use backend-owned framing after a late eligibility check;
        // nevertheless validate the generated introduction before discarding it.
        List<StoneRagGroundingResultEntity> results = new ArrayList<>();
        results.add(checkProse(facts, draft.introduction(), null, execution));
        for (var stone : draft.stones()) results.add(checkProse(facts, stone.explanation(), stone.id(), execution));
        if (draft.knowledge() != null) results.add(checkProse(facts, draft.knowledge(), null, execution));
        return results.stream().filter(result -> !result.valid()).findFirst().orElse(new StoneRagGroundingResultEntity(true, "Checked"));
    }
    private StoneRagGroundingResultEntity checkProse(StoneRagGenerationRequest facts, String prose, Long stoneId, StoneSemanticSearchExecutionEntity execution) {
        if (prose.isBlank()) return new StoneRagGroundingResultEntity(true, "Empty optional introduction");
        if (StoneRagAnswerValidationService.hasVisibleProvenance(prose, facts))
            return new StoneRagGroundingResultEntity(false, "Forbidden source attribution");
        List<StoneRagSourceRequest> applicable = facts.getSources();
        if (stoneId != null) {
            var candidate = facts.getCandidates().stream().filter(stone -> stone.getId() == stoneId).findFirst().orElseThrow(InvalidStoneRagOutputException::new);
            applicable = applicable.stream().filter(source -> source.stoneType() == null || source.stoneType() == candidate.getStoneType()).toList();
        }
        if (applicable.isEmpty()) return invoke(request(facts, prose, stoneId, List.of()), execution);
        List<StoneRagSourceRequest> batch = new ArrayList<>();
        List<StoneRagGroundingResultEntity> results = new ArrayList<>();
        for (var source : applicable) {
            for (var part : split(facts, prose, stoneId, source)) {
                List<StoneRagSourceRequest> proposed = new ArrayList<>(batch); proposed.add(part);
                if (!components.packing().fits(request(facts, prose, stoneId, proposed))) {
                    if (batch.isEmpty()) throw new StoneRagAnswerLimitExceededException();
                    results.add(invoke(request(facts, prose, stoneId, batch), execution)); batch = new ArrayList<>();
                }
                batch.add(part);
            }
        }
        if (!batch.isEmpty()) results.add(invoke(request(facts, prose, stoneId, batch), execution));
        return results.stream().filter(result -> !result.valid()).findFirst().orElse(new StoneRagGroundingResultEntity(true, "All required segments checked"));
    }
    private List<StoneRagSourceRequest> split(StoneRagGenerationRequest facts, String prose, Long stoneId, StoneRagSourceRequest source) {
        if (components.packing().fits(request(facts, prose, stoneId, List.of(source)))) return List.of(source);
        Matcher boundaries = Pattern.compile("(?<=[.!?])\\s+|\\n\\s*\\n").matcher(source.text());
        List<Integer> ends = new ArrayList<>(); while (boundaries.find()) ends.add(boundaries.end()); ends.add(source.text().length());
        List<StoneRagSourceRequest> parts = new ArrayList<>(); int start = 0, lastFit = -1;
        for (int end : ends) {
            var part = part(source, start, end);
            if (components.packing().fits(request(facts, prose, stoneId, List.of(part)))) { lastFit = end; continue; }
            if (lastFit <= start) throw new StoneRagAnswerLimitExceededException();
            parts.add(part(source, start, lastFit)); start = lastFit;
            if (!components.packing().fits(request(facts, prose, stoneId, List.of(part(source, start, end))))) throw new StoneRagAnswerLimitExceededException();
            lastFit = end;
        }
        if (lastFit > start) parts.add(part(source, start, lastFit)); return List.copyOf(parts);
    }
    private StoneRagSourceRequest part(StoneRagSourceRequest source, int start, int end) {
        return new StoneRagSourceRequest(source.id() + "~" + start + ":" + end, source.scope(), source.stoneType(), source.text().substring(start, end));
    }
    private StoneRagGroundingRequest request(StoneRagGenerationRequest facts, String prose, Long stoneId, List<StoneRagSourceRequest> sources) {
        return components.mapping().request().toDto(new StoneRagGenerationRequestToStoneRagGroundingRequestMapper.Input(facts, prose, stoneId, List.copyOf(sources)));
    }
    private StoneRagGroundingResultEntity invoke(StoneRagGroundingRequest request, StoneSemanticSearchExecutionEntity execution) {
        if (!components.packing().fits(request)) throw new StoneRagAnswerLimitExceededException();
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!clock.instant().isBefore(execution.getModelDeadline())) throw new StoneRagAnswerLimitExceededException();
            execution.consumeEvidenceCall(components.settings().maximumEvidenceCalls());
            String output = components.provider().invoke(request, attempt == 0 ? null : "Return exactly valid boolean and a short reason; evaluate all required facts without attribution.").text();
            try {
                JsonNode root = jsonMapper.readTree(output);
                if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("valid", "reason"))
                        || !root.get("valid").isBoolean() || !root.get("reason").isString()) throw new InvalidStoneRagOutputException();
                if (!clock.instant().isBefore(execution.getModelDeadline())) throw new StoneRagAnswerLimitExceededException();
                return new StoneRagGroundingResultEntity(root.get("valid").asBoolean(), root.get("reason").asString());
            } catch (InvalidStoneRagOutputException exception) { if (attempt == 1) throw exception; }
            catch (JacksonException exception) { if (attempt == 1) throw new InvalidStoneRagOutputException(exception); }
        }
        throw new InvalidStoneRagOutputException();
    }
}
