package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.criteria.StoneSemanticPassageCriteria;
import lab.stoneshelter.criteria.StoneSemanticRetrievalCriteria;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.entities.StoneSemanticSearchResultEntity;
import lab.stoneshelter.entities.StoneSemanticSearchStateEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.enums.StoneSemanticSearchMode;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.shared.StoneSemanticSearchRequest;
import lab.stoneshelter.shared.StoneSemanticSearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneSemanticCandidateService {
    private record Components(StoneSemanticSearchRepository repository, StoneSemanticEvidenceService evidence,
            StoneDocumentEmbeddingService embedding, StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper response) {}
    private final Components components;
    private final StoneSemanticSearchSettings settings;
    private final Clock clock;
    public StoneSemanticCandidateService(StoneSemanticSearchRepository repository, StoneSemanticEvidenceService evidence,
            StoneDocumentEmbeddingService embedding, StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper mapper,
            StoneSemanticSearchSettings settings, Clock clock) {
        components = new Components(repository, evidence, embedding, mapper); this.settings = settings; this.clock = clock;
    }

    public StoneSemanticSearchResponse search(StoneSemanticSearchRequest request) {
        StoneSemanticSearchExecutionEntity execution = new StoneSemanticSearchExecutionEntity();
        execution.setDeadline(request.deadline()); execution.setModelDeadline(request.deadline());
        return components.response().toDto(prepare(request.criteria(), execution));
    }

    public StoneSemanticSearchResultEntity prepare(StoneSemanticSearchCriteria selection, StoneSemanticSearchExecutionEntity execution) {
        checkDeadline(execution.getModelDeadline());
        if (selection.wishes().isEmpty()) throw new IllegalArgumentException("Semantic selection requires wishes");
        StoneDocumentEmbeddingEntity query = query(selection.wishes(), execution);
        StoneSemanticRetrievalCriteria retrieval = new StoneSemanticRetrievalCriteria(selection, query,
                components.repository().findPassages(new StoneSemanticPassageCriteria(query, selection.exact().getStoneTypes(),
                        new StoneSemanticPassageCriteria.Bounds(settings.documentLimit(), settings.documentMinimumSimilarity()))),
                new StoneSemanticRetrievalCriteria.Bounds(settings.candidateLimit(), settings.cardMinimumSimilarity()));
        List<StoneSemanticCandidateEntity> candidates = components.evidence().evaluateCandidates(components.repository().search(retrieval).candidates(),
                selection.wishes(), execution, false).stream().filter(candidate -> !candidate.getContradicted() && !candidate.getMatchedWishes().isEmpty())
                .sorted(order()).toList();
        StoneSemanticSearchMode mode = StoneSemanticSearchMode.SEMANTIC;
        if (candidates.isEmpty() && selection.exact().hasNarrowing()) {
            candidates = components.evidence().evaluateCandidates(components.repository().searchAlternatives(retrieval), selection.wishes(), execution, true)
                    .stream().filter(candidate -> !candidate.getContradicted()).toList();
            mode = StoneSemanticSearchMode.EXACT_FALLBACK;
        }
        checkDeadline(execution.getModelDeadline());
        List<StoneSemanticCandidateEntity> surviving = eligible(candidates, selection);
        if (!candidates.isEmpty() && surviving.isEmpty() && !execution.getRefreshSpent()) {
            execution.setRefreshSpent(true);
            return prepare(selection, execution);
        }
        return result(selection, surviving, surviving.isEmpty() ? StoneSemanticSearchMode.NO_MATCH : mode);
    }

    public List<StoneSemanticCandidateEntity> eligible(List<StoneSemanticCandidateEntity> candidates, StoneSemanticSearchCriteria selection) {
        Set<Long> eligible = components.repository().findEligible(candidates.stream().map(candidate -> candidate.getStone().getId()).toList(), selection.exact())
                .stream().map(StoneEntity::getId).collect(Collectors.toSet());
        return candidates.stream().filter(candidate -> eligible.contains(candidate.getStone().getId())).toList();
    }

    public StoneSemanticSearchResultEntity result(StoneSemanticSearchCriteria selection, List<StoneSemanticCandidateEntity> candidates, StoneSemanticSearchMode mode) {
        StoneSemanticSearchResultEntity result = new StoneSemanticSearchResultEntity();
        result.setCandidates(List.copyOf(candidates)); result.setMode(mode); result.setFoundCount(candidates.size()); result.setHasMore(candidates.size() > 5);
        StoneSemanticSearchStateEntity state = new StoneSemanticSearchStateEntity();
        state.setExact(selection.exact()); state.setWishes(selection.wishes()); state.setMode(mode); state.setActualSelection(!candidates.isEmpty());
        result.setState(state);
        StringBuilder text = new StringBuilder(mode == StoneSemanticSearchMode.NO_MATCH ? "Suitable candidates were not found."
                : mode == StoneSemanticSearchMode.EXACT_FALLBACK ? "Suitable semantic candidates were not found. Here are exact alternatives; semantic wishes remain unconfirmed."
                : "Found " + candidates.size() + " suitable candidates in the checked set.");
        for (var candidate : candidates.stream().limit(5).toList()) {
            text.append("\n").append(candidate.getStone().getName());
            if (!candidate.getUnmatchedWishes().isEmpty()) text.append(" — unconfirmed wishes: ").append(candidate.getUnmatchedWishes().stream()
                    .map(StoneSemanticWish::text).collect(Collectors.joining(", ")));
            text.append('.');
        }
        if (result.getHasMore()) text.append("\nMore candidates were found.");
        result.setText(text.toString()); return result;
    }

    private StoneDocumentEmbeddingEntity query(List<StoneSemanticWish> wishes, StoneSemanticSearchExecutionEntity execution) {
        String text = wishes.stream().map(wish -> wish.key() + ": " + wish.text()).collect(Collectors.joining("\n"));
        StoneDocumentEmbeddingEntity cached = execution.getQueryCache().get(text);
        if (cached != null) return cached;
        if (execution.getQueryEmbeddings() >= 2) throw new StoneRagAnswerLimitExceededException();
        Instant deadline = clock.instant().plusSeconds(50);
        if (execution.getModelDeadline().isBefore(deadline)) deadline = execution.getModelDeadline();
        execution.setQueryEmbeddings(execution.getQueryEmbeddings() + 1);
        StoneDocumentEmbeddingEntity query = components.embedding().embed(new StoneDocumentEmbeddingCriteria(text, true, deadline, ModelAdmissionMode.CUSTOMER));
        execution.getQueryCache().put(text, query); return query;
    }

    private Comparator<StoneSemanticCandidateEntity> order() {
        Comparator<Double> scores = Comparator.nullsLast(Comparator.reverseOrder());
        return Comparator.comparing((StoneSemanticCandidateEntity candidate) -> !candidate.getCardSupported())
                .thenComparing(candidate -> candidate.getCardSupported() ? candidate.getCardScore() : candidate.getSupportingDocumentScore(), scores)
                .thenComparing(candidate -> candidate.getStone().getId());
    }
    private void checkDeadline(Instant deadline) { if (deadline == null || !clock.instant().isBefore(deadline)) throw new StoneChatUnavailableException(); }
}
