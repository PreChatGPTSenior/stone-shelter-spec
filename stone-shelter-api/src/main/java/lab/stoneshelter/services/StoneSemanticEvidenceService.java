package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticEvidenceResultEntity;
import lab.stoneshelter.mappers.dtos.StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.enums.StoneSemanticEvidenceVerdict;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper;
import lab.stoneshelter.shared.StoneSemanticEvidenceCandidateRequest;
import lab.stoneshelter.shared.StoneSemanticEvidenceRequest;
import lab.stoneshelter.shared.StoneSemanticEvidenceResponse;
import lab.stoneshelter.shared.StoneSemanticEvidenceSourceRequest;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneSemanticEvidenceService {
    private record Mapping(StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper response,
            StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper input) {}
    private record Components(StoneSemanticEvidenceProvider provider, StoneSemanticEvidenceValidationService validation,
            Mapping mapping, OllamaSemanticEvidenceProvider packing) {}
    private record Unit(StoneSemanticEvidenceSourceRequest part, StoneSemanticWishRequest wish, String cacheKey) {}
    private final Components components;
    private final StoneSemanticSearchSettings settings;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StoneSemanticEvidenceService(StoneSemanticEvidenceProvider provider, StoneSemanticEvidenceValidationService validation,
            StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper mapper, OllamaSemanticEvidenceProvider packing,
            StoneSemanticSearchSettings settings, Clock clock, StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper inputMapper) {
        components = new Components(provider, validation, new Mapping(mapper, inputMapper), packing); this.settings = settings; this.clock = clock;
    }

    public StoneSemanticEvidenceResponse checkCandidates(List<StoneSemanticEvidenceCandidateRequest> candidates,
            List<StoneSemanticWishRequest> wishes, StoneSemanticSearchExecutionEntity execution, boolean cardOnly) {
        return components.mapping().response().toDto(completeSources(candidateSources(candidates, cardOnly), wishes, execution));
    }

    private List<StoneSemanticEvidenceSourceRequest> candidateSources(List<StoneSemanticEvidenceCandidateRequest> candidates, boolean cardOnly) {
        Map<String, StoneSemanticEvidenceSourceRequest> sources = new LinkedHashMap<>();
        for (var candidate : candidates) {
            if (candidate.id() <= 0 || candidate.stoneType() == null || candidate.card() == null || candidate.card().isBlank()) {
                throw new IllegalArgumentException("Current candidate facts are required");
            }
            addSource(sources, new StoneSemanticEvidenceSourceRequest("card:" + candidate.id(), StoneSemanticSupportSource.CARD,
                    candidate.stoneType(), candidate.card()));
            for (var source : candidate.sources()) {
                if (source.scope() == StoneSemanticSupportSource.CARD
                        || source.scope() == StoneSemanticSupportSource.TYPE && source.stoneType() != candidate.stoneType()
                        || source.scope() == StoneSemanticSupportSource.SHARED && source.stoneType() != null) {
                    throw new InvalidStoneSemanticSearchOutputException();
                }
                if (!cardOnly) addSource(sources, source);
            }
        }
        return List.copyOf(sources.values());
    }

    public StoneSemanticEvidenceResponse checkSources(List<StoneSemanticEvidenceSourceRequest> sources,
            List<StoneSemanticWishRequest> wishes, StoneSemanticSearchExecutionEntity execution) {
        return components.mapping().response().toDto(completeSources(sources, wishes, execution));
    }

    private StoneSemanticEvidenceResultEntity completeSources(List<StoneSemanticEvidenceSourceRequest> sources,
            List<StoneSemanticWishRequest> wishes, StoneSemanticSearchExecutionEntity execution) {
        if (execution.getModelDeadline() == null || execution.getDeadline() == null) throw new IllegalArgumentException("Original turn bounds are required");
        if (wishes.isEmpty() || sources.isEmpty()) return new StoneSemanticEvidenceResultEntity(List.of());
        if (wishes.stream().anyMatch(wish -> wish == null || wish.key() == null || !wish.key().matches("[a-z][a-z0-9_-]{0,31}")
                || wish.text() == null || wish.text().isBlank()) || wishes.stream().map(StoneSemanticWishRequest::key).distinct().count() != wishes.size()) {
            throw new IllegalArgumentException("Distinct explicit wishes are required");
        }
        Map<String, StoneSemanticEvidenceSourceRequest> unique = new LinkedHashMap<>();
        sources.forEach(source -> addSource(unique, source));
        Map<String, StoneSemanticEvidenceSourceRequest> original = new LinkedHashMap<>();
        List<Unit> remaining = new ArrayList<>();
        for (var source : unique.values()) {
            for (var wish : wishes) {
                String key = cacheKey(source, wish);
                original.put(key, source);
                if (!execution.getEvidenceCache().containsKey(key)) {
                    for (var part : split(source, wish, execution.getModelDeadline())) remaining.add(new Unit(part, wish, key));
                }
            }
        }
        Map<String, List<StoneSemanticEvidenceResultEntity.Judgement>> partial = new LinkedHashMap<>();
        while (!remaining.isEmpty()) {
            checkDeadline(execution.getModelDeadline());
            Unit first = remaining.getFirst();
            List<StoneSemanticEvidenceSourceRequest> batchSources = new ArrayList<>(List.of(first.part()));
            List<StoneSemanticWishRequest> batchWishes = new ArrayList<>(List.of(first.wish()));
            for (var wish : wishes) {
                if (batchWishes.contains(wish) || !hasUnit(remaining, first.part(), wish)) continue;
                List<StoneSemanticWishRequest> proposed = new ArrayList<>(batchWishes); proposed.add(wish);
                if (components.packing().fits(new StoneSemanticEvidenceRequest(proposed, batchSources, execution.getModelDeadline()))) batchWishes = proposed;
            }
            for (Unit unit : remaining) {
                if (unit.part().scope() != first.part().scope() || batchSources.stream().anyMatch(source -> source.id().equals(unit.part().id()))) continue;
                boolean allPresent = true;
                for (var wish : batchWishes) allPresent &= hasUnit(remaining, unit.part(), wish);
                if (!allPresent) continue;
                List<StoneSemanticEvidenceSourceRequest> proposed = new ArrayList<>(batchSources); proposed.add(unit.part());
                if (components.packing().fits(new StoneSemanticEvidenceRequest(batchWishes, proposed, execution.getModelDeadline()))) batchSources = proposed;
            }
            StoneSemanticEvidenceRequest request = new StoneSemanticEvidenceRequest(batchWishes, batchSources, execution.getModelDeadline());
            StoneSemanticEvidenceResultEntity result = invokeValidated(request, execution);
            for (var judgement : result.judgements()) {
                Unit unit = remaining.stream().filter(value -> value.part().id().equals(judgement.sourceId())
                        && value.wish().key().equals(judgement.key())).findFirst().orElseThrow(InvalidStoneSemanticSearchOutputException::new);
                partial.computeIfAbsent(unit.cacheKey(), ignored -> new ArrayList<>()).add(judgement);
                remaining.remove(unit);
            }
        }
        // Publish cache entries only after all required segments were processed.
        for (var entry : partial.entrySet()) {
            var source = original.get(entry.getKey());
            var chosen = entry.getValue().stream().filter(judgement -> judgement.verdict() == StoneSemanticEvidenceVerdict.CONTRADICTED).findFirst()
                    .orElseGet(() -> entry.getValue().stream().filter(judgement -> judgement.verdict() == StoneSemanticEvidenceVerdict.SUPPORTED).findFirst()
                            .orElse(entry.getValue().getFirst()));
            execution.getEvidenceCache().put(entry.getKey(), new StoneSemanticEvidenceResultEntity.Judgement(chosen.key(), source.id(), chosen.verdict(), chosen.quote()));
        }
        List<StoneSemanticEvidenceResultEntity.Judgement> complete = new ArrayList<>();
        for (var source : unique.values()) for (var wish : wishes) complete.add(execution.getEvidenceCache().get(cacheKey(source, wish)));
        return new StoneSemanticEvidenceResultEntity(complete);
    }

    public List<StoneSemanticCandidateEntity> evaluateCandidates(List<StoneSemanticCandidateEntity> candidates,
            List<StoneSemanticWish> wishes, StoneSemanticSearchExecutionEntity execution, boolean cardOnly) {
        StoneSemanticEvidenceResultEntity result = completeSources(candidateSources(candidates.stream()
                .map(components.mapping().input()::toDto).toList(), cardOnly), components.mapping().input().toDto(wishes), execution);
        return candidates.stream().map(candidate -> applyEvidence(candidate, wishes, result)).toList();
    }

    private StoneSemanticCandidateEntity applyEvidence(StoneSemanticCandidateEntity candidate, List<StoneSemanticWish> wishes,
            StoneSemanticEvidenceResultEntity result) {
        String cardId = "card:" + candidate.getStone().getId();
        Map<String, Double> documentScores = new LinkedHashMap<>();
        candidate.getPassages().forEach(passage -> documentScores.put(passage.fragment().getDocumentKey() + "@"
                + passage.fragment().getSourceVersion() + "#" + passage.fragment().getFragmentOrder(), passage.score()));
        List<StoneSemanticEvidenceResultEntity.Judgement> evidence = result.judgements().stream()
                .filter(judgement -> judgement.sourceId().equals(cardId) || documentScores.containsKey(judgement.sourceId())).toList();
        candidate.setEvidence(evidence);
        candidate.setContradicted(evidence.stream().anyMatch(judgement -> judgement.sourceId().equals(cardId)
                && judgement.verdict() == StoneSemanticEvidenceVerdict.CONTRADICTED));
        List<StoneSemanticWish> matched = new ArrayList<>(), unmatched = new ArrayList<>();
        boolean cardSupported = false;
        Double supportingDocumentScore = null;
        for (var wish : wishes) {
            boolean supported = false;
            for (var judgement : evidence) {
                if (!judgement.key().equals(wish.key()) || judgement.verdict() != StoneSemanticEvidenceVerdict.SUPPORTED) continue;
                supported = true;
                if (judgement.sourceId().equals(cardId)) cardSupported = true;
                else {
                    Double score = documentScores.get(judgement.sourceId());
                    if (score != null && (supportingDocumentScore == null || score > supportingDocumentScore)) supportingDocumentScore = score;
                }
            }
            if (supported) matched.add(wish); else unmatched.add(wish);
        }
        candidate.setMatchedWishes(List.copyOf(matched)); candidate.setUnmatchedWishes(List.copyOf(unmatched));
        candidate.setCardSupported(cardSupported); candidate.setSupportingDocumentScore(supportingDocumentScore);
        return candidate;
    }

    private StoneSemanticEvidenceResultEntity invokeValidated(StoneSemanticEvidenceRequest request, StoneSemanticSearchExecutionEntity execution) {
        String correction = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            checkDeadline(execution.getModelDeadline());
            execution.consumeEvidenceCall(settings.maximumEvidenceCalls());
            String output = components.provider().invoke(request,
                    correction).text();
            try {
                StoneSemanticEvidenceResultEntity result = components.validation().validate(output, request);
                checkDeadline(execution.getModelDeadline());
                return result;
            } catch (InvalidStoneSemanticSearchOutputException exception) {
                if (attempt == 1) throw exception;
                correction = exception.getCorrection() == null
                        ? "Return every supplied source/wish pair once, known IDs, and short verbatim quotes; absence is UNKNOWN/null."
                        : exception.getCorrection();
            }
        }
        throw new InvalidStoneSemanticSearchOutputException();
    }

    private List<StoneSemanticEvidenceSourceRequest> split(StoneSemanticEvidenceSourceRequest source,
            StoneSemanticWishRequest wish, Instant deadline) {
        if (components.packing().fits(new StoneSemanticEvidenceRequest(List.of(wish), List.of(source), deadline))) return List.of(source);
        List<StoneSemanticEvidenceSourceRequest> parts = new ArrayList<>();
        Matcher boundaries = Pattern.compile("(?<=[.!?])\\s+|\\n\\s*\\n").matcher(source.text());
        List<Integer> ends = new ArrayList<>();
        while (boundaries.find()) ends.add(boundaries.end());
        ends.add(source.text().length());
        int start = 0;
        int lastFit = -1;
        for (int end : ends) {
            var proposed = part(source, start, end);
            if (components.packing().fits(new StoneSemanticEvidenceRequest(List.of(wish), List.of(proposed), deadline))) { lastFit = end; continue; }
            if (lastFit <= start) throw new StoneSemanticSearchLimitExceededException();
            parts.add(part(source, start, lastFit)); start = lastFit;
            proposed = part(source, start, end);
            if (!components.packing().fits(new StoneSemanticEvidenceRequest(List.of(wish), List.of(proposed), deadline))) throw new StoneSemanticSearchLimitExceededException();
            lastFit = end;
        }
        if (lastFit > start) parts.add(part(source, start, lastFit));
        return List.copyOf(parts);
    }

    private StoneSemanticEvidenceSourceRequest part(StoneSemanticEvidenceSourceRequest source, int start, int end) {
        return new StoneSemanticEvidenceSourceRequest(source.id() + "~" + start + ":" + end, source.scope(), source.stoneType(), source.text().substring(start, end));
    }

    private boolean hasUnit(List<Unit> units, StoneSemanticEvidenceSourceRequest source, StoneSemanticWishRequest wish) {
        return units.stream().anyMatch(unit -> unit.part().equals(source) && unit.wish().equals(wish));
    }

    private String cacheKey(StoneSemanticEvidenceSourceRequest source, StoneSemanticWishRequest wish) {
        return jsonMapper.writeValueAsString(List.of(source, wish));
    }

    private void addSource(Map<String, StoneSemanticEvidenceSourceRequest> sources, StoneSemanticEvidenceSourceRequest source) {
        if (source == null || source.id() == null || source.id().isBlank() || source.scope() == null
                || source.text() == null || source.text().isBlank()
                || source.scope() == StoneSemanticSupportSource.SHARED && source.stoneType() != null
                || source.scope() != StoneSemanticSupportSource.SHARED && source.stoneType() == null) {
            throw new IllegalArgumentException("Complete source identity/scope/text is required");
        }
        var previous = sources.putIfAbsent(source.id(), source);
        if (previous != null && !Objects.equals(previous, source)) throw new InvalidStoneSemanticSearchOutputException();
    }

    private void checkDeadline(Instant deadline) { if (!clock.instant().isBefore(deadline)) throw new StoneChatUnavailableException(); }
}
