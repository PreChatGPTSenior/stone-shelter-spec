package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneExactSearchPreparationEntity;
import lab.stoneshelter.entities.StoneSemanticPreparedTurnEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.entities.StoneSemanticSearchStateEntity;
import lab.stoneshelter.enums.StoneSemanticSearchMode;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.shared.StoneSemanticSearchDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneSemanticSearchService {
    private record Interpretation(StoneSemanticSearchProvider provider, StoneSemanticSearchValidationService validation) {}
    private record Mapping(StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper exact,
            StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper criteria,
            StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper turn) {}
    private record Components(Interpretation interpretation, StoneExactSearchService exact, StoneSemanticCandidateService candidates, Mapping mapping) {}
    private final Components components;
    private final Clock clock;
    public StoneSemanticSearchService(StoneSemanticSearchProvider provider, StoneSemanticSearchValidationService validation,
            StoneExactSearchService exact, StoneSemanticCandidateService candidates,
            StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper exactMapper,
            StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper criteriaMapper,
            StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper turnMapper, Clock clock) {
        components = new Components(new Interpretation(provider, validation), exact, candidates, new Mapping(exactMapper, criteriaMapper, turnMapper));
        this.clock = clock;
    }
    public StoneChatTurnEntity complete(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, Instant deadline) {
        StoneSemanticSearchExecutionEntity execution = new StoneSemanticSearchExecutionEntity();
        execution.setDeadline(deadline); execution.setModelDeadline(deadline);
        StoneSemanticPreparedTurnEntity prepared = prepare(turn, history, execution);
        return prepared.selection() == null ? prepared.turn() : components.mapping().turn().toEntity(prepared);
    }

    public StoneSemanticPreparedTurnEntity prepare(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, StoneSemanticSearchExecutionEntity execution) {
        StoneSemanticSearchDecision decision = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            checkDeadline(execution.getModelDeadline());
            execution.setInterpretationCalls(execution.getInterpretationCalls() + 1);
            String output = components.interpretation().provider().invoke(turn, history, execution.getModelDeadline(),
                    attempt == 0 ? null : "Return the exact schema and all explicit wishes. Missing color/use are JSON null; never invent preferences or IDs.").text();
            try { decision = components.interpretation().validation().validate(output); break; }
            catch (InvalidStoneSemanticSearchOutputException exception) { if (attempt == 1) throw exception; }
        }
        checkDeadline(execution.getModelDeadline());
        return prepare(turn, history, decision, execution);
    }

    public StoneSemanticPreparedTurnEntity prepare(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history,
            StoneSemanticSearchDecision decision, StoneSemanticSearchExecutionEntity execution) {
        return prepare(turn, history, decision, execution, false);
    }

    public StoneSemanticPreparedTurnEntity prepare(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history,
            StoneSemanticSearchDecision decision, StoneSemanticSearchExecutionEntity execution, boolean knownSelection) {
        checkDeadline(execution.getModelDeadline());
        StoneSemanticSearchCriteria interpreted = components.mapping().criteria().toEntity(decision);
        Map<String, StoneSemanticWish> wishes = new LinkedHashMap<>();
        if (!decision.getResetSearch()) previousWishes(history).forEach(wish -> wishes.put(wish.key(), wish));
        interpreted.wishes().forEach(wish -> wishes.put(wish.key(), wish));
        StoneExactSearchPreparationEntity preparation = components.exact().prepare(turn, history, components.mapping().exact().toDto(decision), !wishes.isEmpty(), knownSelection);
        StoneSemanticSearchCriteria selection = new StoneSemanticSearchCriteria(preparation.criteria(), List.copyOf(wishes.values()));
        if (preparation.earlyTurn() != null) return new StoneSemanticPreparedTurnEntity(
                retainState(preparation.earlyTurn(), selection), selection, null, execution);
        if (wishes.isEmpty()) return new StoneSemanticPreparedTurnEntity(retainState(components.exact().search(turn, preparation.criteria(), execution), selection),
                selection, null, execution);
        return new StoneSemanticPreparedTurnEntity(turn, selection, components.candidates().prepare(selection, execution), execution);
    }

    private StoneChatTurnEntity retainState(StoneChatTurnEntity turn, StoneSemanticSearchCriteria selection) {
        StoneSemanticSearchStateEntity state = new StoneSemanticSearchStateEntity();
        state.setExact(turn.getExactSearchCriteria()); state.setWishes(selection.wishes()); state.setMode(StoneSemanticSearchMode.NO_MATCH);
        state.setActualSelection(turn.getStones() != null && !turn.getStones().isEmpty()); turn.setSemanticSearchState(state); return turn;
    }
    private List<StoneSemanticWish> previousWishes(List<StoneChatTurnEntity> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).getSemanticSearchState() != null) return history.get(index).getSemanticSearchState().getWishes();
            if (history.get(index).getExactSearchCriteria() != null) return List.of();
        }
        return List.of();
    }
    private void checkDeadline(Instant deadline) { if (deadline == null || !clock.instant().isBefore(deadline)) throw new StoneChatUnavailableException(); }
}
