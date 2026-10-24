package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneRagAnswerCriteria;
import lab.stoneshelter.criteria.StoneRagKnowledgeCriteria;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneRagAnswerAssemblyEntity;
import lab.stoneshelter.entities.StoneRagAnswerResultEntity;
import lab.stoneshelter.entities.StoneRagCompletionEvidenceEntity;
import lab.stoneshelter.entities.StoneRagExecutionEntity;
import lab.stoneshelter.entities.StoneRagPreparedTurnEntity;
import lab.stoneshelter.entities.StoneRagTurnStateEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticPreparedTurnEntity;
import lab.stoneshelter.entities.StoneSemanticSearchResultEntity;
import lab.stoneshelter.entities.StoneSemanticSearchStateEntity;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneSemanticSearchMode;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneRagDecisionToStoneSemanticSearchDecisionMapper;
import lab.stoneshelter.mappers.entities.StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneRagDecisionToStoneRagAnswerCriteriaMapper;
import lab.stoneshelter.mappers.entities.StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.repositories.StoneRagKnowledgeRepository;
import lab.stoneshelter.shared.StoneRagDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneRagConversationService {
    private record Routing(StoneRagRoutingProvider provider, StoneRagDecisionValidationService validation,
            StoneRagDecisionToStoneSemanticSearchDecisionMapper selection) {}
    private record Selection(StoneSemanticSearchService preparation, StoneSemanticCandidateService candidates,
            StoneExactSearchService exact, StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper turn) {}
    private record Knowledge(StoneRagKnowledgeService preparation, StoneRagKnowledgeRepository cards, StoneChatReferenceService references) {}
    private record Mapping(StoneRagDecisionToStoneRagAnswerCriteriaMapper criteria, StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper request,
            StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper turn) {}
    private record Components(Routing routing, Selection selection, Knowledge knowledge, Mapping mapping) {}
    private final Components components;
    private final StoneRagAnswerService answers;
    private final StoneChatAvailabilityService availability;
    private record Timing(Clock clock, StoneRagSettings settings) {}
    private final Timing timing;
    public StoneRagConversationService(StoneRagRoutingProvider provider, StoneRagDecisionValidationService validation,
            StoneRagDecisionToStoneSemanticSearchDecisionMapper selectionMapper, StoneSemanticSearchService semantic,
            StoneSemanticCandidateService candidates, StoneExactSearchService exact, StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper semanticTurnMapper,
            StoneRagKnowledgeService knowledge, StoneRagKnowledgeRepository cards, StoneChatReferenceService references,
            StoneRagDecisionToStoneRagAnswerCriteriaMapper criteriaMapper, StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper requestMapper,
            StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper turnMapper, StoneRagAnswerService answers,
            StoneChatAvailabilityService availability, Clock clock, StoneRagSettings settings) {
        components = new Components(new Routing(provider, validation, selectionMapper), new Selection(semantic, candidates, exact, semanticTurnMapper),
                new Knowledge(knowledge, cards, references), new Mapping(criteriaMapper, requestMapper, turnMapper));
        this.answers = answers; this.availability = availability; timing = new Timing(clock, settings);
    }
    public StoneRagPreparedTurnEntity complete(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, Instant deadline) {
        long controlEpoch = availability.captureSelectionControlEpoch();
        StoneRagExecutionEntity execution = new StoneRagExecutionEntity(); execution.setDeadline(deadline); execution.setModelDeadline(deadline.minus(timing.settings().finalizationReserve()));
        StoneRagDecision decision = interpret(turn, history, execution);
        if (decision.getMode() == StoneRagMode.KNOWLEDGE) return knowledge(turn, history, decision, execution);
        List<StoneChatTurnEntity> selectionHistory = history.stream().filter(previous -> previous.getRagState() == null || previous.getRagState().mode() != StoneRagMode.KNOWLEDGE).toList();
        StoneSemanticPreparedTurnEntity prepared = components.selection().preparation().prepare(turn, selectionHistory,
                components.routing().selection().toDto(decision), execution, decision.getMode() == StoneRagMode.SELECTION);
        if (prepared.selection() == null && (prepared.turn().getStones() == null || prepared.turn().getStones().isEmpty())) {
            prepared.turn().setRagState(new StoneRagTurnStateEntity(decision.getMode(), StoneRagCompletionKind.CLARIFICATION,
                    components.knowledge().references().selectionReferenceTurnId(history), false));
            return new StoneRagPreparedTurnEntity(prepared.turn(), null);
        }
        StoneSemanticSearchResultEntity selection = prepared.selection() == null ? exactPool(prepared, execution) : prepared.selection();
        return selection(turn, history, decision, prepared.criteria(), selection, execution, controlEpoch);
    }
    private StoneRagDecision interpret(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, StoneRagExecutionEntity execution) {
        for (int attempt = 0; attempt < 2; attempt++) {
            checkWork(execution); execution.setInterpretationCalls(execution.getInterpretationCalls() + 1);
            String output = components.routing().provider().invoke(turn, history, execution.getModelDeadline(), attempt == 0 ? null
                    : "Use exact fields/mode. Keep every asked condition, no unasked wishes/dates. Care explanation is topic, not selection use.").text();
            try { return components.routing().validation().validate(output); }
            catch (InvalidStoneRagOutputException exception) { if (attempt == 1) throw exception; }
        }
        throw new InvalidStoneRagOutputException();
    }
    private StoneRagPreparedTurnEntity knowledge(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, StoneRagDecision decision, StoneRagExecutionEntity execution) {
        Long id = null;
        if (decision.getReference() != null) {
            var reference = components.knowledge().references().resolve(decision.getReference(), turn, history);
            if (reference.isEmpty()) return clarification(turn, history, "That reference is ambiguous or the stone is no longer available.");
            id = reference.getAsLong();
        }
        var knowledge = components.knowledge().preparation().prepare(new StoneRagKnowledgeCriteria(decision.getTopic(), decision.getCriteria().stoneTypes(), id), execution);
        if (id != null && knowledge.getStone() == null) return clarification(turn, history, "That stone is no longer available.");
        StoneRagAnswerCriteria criteria = components.mapping().criteria().toEntity(decision); criteria.setQuestion(turn.getMessage());
        criteria.setReference(knowledge.getStone()); criteria.setPassages(knowledge.getPassages()); criteria.setDeadline(execution.getModelDeadline());
        StoneRagAnswerResultEntity answer = answers.prepare(components.mapping().request().toDto(criteria), execution);
        checkDeadline(execution);
        if (id != null && components.knowledge().cards().findById(id).isEmpty()) return clarification(turn, history, "That stone is no longer available.");
        StoneSemanticSearchResultEntity result = new StoneSemanticSearchResultEntity(); result.setState(previousState(history));
        if (knowledge.getStone() != null) result.setCandidates(List.of(candidate(knowledge.getStone())));
        StoneRagTurnStateEntity state = new StoneRagTurnStateEntity(StoneRagMode.KNOWLEDGE, StoneRagCompletionKind.GENERATED,
                components.knowledge().references().selectionReferenceTurnId(history), false);
        return new StoneRagPreparedTurnEntity(components.mapping().turn().toEntity(new StoneRagAnswerAssemblyEntity(turn, result, answer.answer(), state)), null);
    }
    private StoneRagPreparedTurnEntity selection(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, StoneRagDecision decision,
            StoneSemanticSearchCriteria criteria, StoneSemanticSearchResultEntity selected, StoneRagExecutionEntity execution, long controlEpoch) {
        if (selected.getCandidates().isEmpty()) return assembled(turn, history, criteria, selected, null, execution, controlEpoch);
        StoneRagAnswerCriteria answerCriteria = components.mapping().criteria().toEntity(decision); answerCriteria.setQuestion(turn.getMessage());
        answerCriteria.setCandidates(selected.getCandidates().stream().limit(5).toList()); answerCriteria.setDeadline(execution.getModelDeadline());
        answerCriteria.setSelectionMode(criteria.wishes().isEmpty() ? null : selected.getMode());
        if (timing.clock().instant().isBefore(execution.getModelDeadline()) && additionalTopic(decision.getTopic(), criteria)) {
            var knowledge = components.knowledge().preparation().prepare(new StoneRagKnowledgeCriteria(decision.getTopic(),
                    answerCriteria.getCandidates().stream().map(candidate -> candidate.getStone().getStoneType()).distinct().toList(), null), execution);
            answerCriteria.setPassages(knowledge.getPassages());
        }
        availability.assertReady();
        StoneRagAnswerResultEntity answer = answers.prepare(components.mapping().request().toDto(answerCriteria), execution);
        checkDeadline(execution);
        List<StoneSemanticCandidateEntity> wholePool = components.selection().candidates().eligible(selected.getCandidates(), criteria);
        Set<Long> shown = answerCriteria.getCandidates().stream().map(candidate -> candidate.getStone().getId()).collect(Collectors.toSet());
        List<StoneSemanticCandidateEntity> survivingShown = answer.completion() == StoneRagCompletionKind.TEMPLATE
                ? wholePool : wholePool.stream().filter(candidate -> shown.contains(candidate.getStone().getId())).toList();
        if (survivingShown.isEmpty() && !execution.getRefreshSpent()) {
            execution.setRefreshSpent(true);
            StoneSemanticSearchResultEntity refreshed;
            if (criteria.wishes().isEmpty()) {
                components.selection().exact().search(turn, criteria.exact(), execution);
                refreshed = exactPool(new StoneSemanticPreparedTurnEntity(turn, criteria, null, execution), execution);
            } else refreshed = components.selection().candidates().prepare(criteria, execution);
            return selection(turn, history, decision, criteria, refreshed, execution, controlEpoch);
        }
        if (survivingShown.isEmpty()) selected = components.selection().candidates().result(criteria, List.of(), StoneSemanticSearchMode.NO_MATCH);
        else {
            selected.setCandidates(survivingShown); selected.setFoundCount(criteria.wishes().isEmpty() ? components.selection().exact().count(criteria.exact()) : wholePool.size());
            selected.setHasMore(selected.getFoundCount() > 5); selected.getState().setActualSelection(true);
            if (answer.completion() == StoneRagCompletionKind.TEMPLATE) selected.setText(template(selected));
        }
        return assembled(turn, history, criteria, selected, answer, execution, controlEpoch);
    }
    private StoneRagPreparedTurnEntity assembled(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, StoneSemanticSearchCriteria criteria,
            StoneSemanticSearchResultEntity selected, StoneRagAnswerResultEntity answer, StoneRagExecutionEntity execution, long controlEpoch) {
        StoneRagCompletionKind kind = answer == null || selected.getCandidates().isEmpty() ? StoneRagCompletionKind.TEMPLATE : answer.completion();
        StoneRagTurnStateEntity state = new StoneRagTurnStateEntity(StoneRagMode.SELECTION, kind, selected.getCandidates().isEmpty()
                ? components.knowledge().references().selectionReferenceTurnId(history) : turn.getTurnId(), !selected.getCandidates().isEmpty());
        StoneChatTurnEntity completed = components.mapping().turn().toEntity(new StoneRagAnswerAssemblyEntity(turn, selected,
                answer == null || kind != StoneRagCompletionKind.GENERATED ? null : answer.answer(), state));
        StoneRagCompletionEvidenceEntity proof = answer != null && kind == StoneRagCompletionKind.TEMPLATE && !selected.getCandidates().isEmpty()
                ? new StoneRagCompletionEvidenceEntity(execution.getDeadline(), controlEpoch,
                        new StoneSemanticPreparedTurnEntity(turn, criteria, selected, execution), answer.failure()) : null;
        return new StoneRagPreparedTurnEntity(completed, proof);
    }
    private StoneSemanticSearchResultEntity exactPool(StoneSemanticPreparedTurnEntity prepared, StoneRagExecutionEntity execution) {
        List<StoneSemanticCandidateEntity> candidates = new ArrayList<>();
        execution.getExactResult().getStones().forEach(stone -> components.knowledge().cards().findById(stone.getId()).ifPresent(current -> candidates.add(candidate(current))));
        if (candidates.isEmpty() && !execution.getExactResult().getStones().isEmpty() && !execution.getRefreshSpent()) {
            execution.setRefreshSpent(true); components.selection().exact().search(prepared.turn(), prepared.criteria().exact(), execution); return exactPool(prepared, execution);
        }
        StoneSemanticSearchResultEntity result = components.selection().candidates().result(prepared.criteria(), candidates, StoneSemanticSearchMode.NO_MATCH);
        result.setFoundCount(execution.getExactResult().getTotal()); result.setHasMore(result.getFoundCount() > 5);
        result.setText(candidates.isEmpty() ? "No available stones match these conditions." : template(result)); return result;
    }
    private String template(StoneSemanticSearchResultEntity selected) {
        if (selected.getState().getWishes().isEmpty()) return "Found " + selected.getFoundCount() + " matching available stones."
                + (selected.getHasMore() ? " More candidates were found." : "");
        return components.selection().candidates().result(new StoneSemanticSearchCriteria(selected.getState().getExact(), selected.getState().getWishes()), selected.getCandidates(), selected.getMode()).getText();
    }
    private StoneSemanticCandidateEntity candidate(StoneEntity stone) { StoneSemanticCandidateEntity candidate = new StoneSemanticCandidateEntity(); candidate.setStone(stone); return candidate; }
    private boolean additionalTopic(String topic, StoneSemanticSearchCriteria criteria) {
        if (topic == null || topic.isBlank()) return false;
        String known = criteria.wishes().stream().map(wish -> wish.text().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
        return List.of(topic.toLowerCase(Locale.ROOT).split("\\W+")).stream().anyMatch(word -> !word.isBlank() && !known.contains(word)
                && !word.matches("stone|stones|for|a|the|small|medium|large|basalt|granite|obsidian|pumice|limestone|sandstone|shale|marble|gneiss|slate"));
    }
    private StoneSemanticSearchStateEntity previousState(List<StoneChatTurnEntity> history) {
        StoneSemanticSearchStateEntity state = new StoneSemanticSearchStateEntity(); state.setExact(new StoneExactSearchCriteria()); state.setMode(StoneSemanticSearchMode.NO_MATCH);
        for (int index = history.size() - 1; index >= 0; index--) {
            var previous = history.get(index);
            if (previous.getSemanticSearchState() != null) { state.setExact(previous.getSemanticSearchState().getExact()); state.setWishes(previous.getSemanticSearchState().getWishes()); state.setMode(previous.getSemanticSearchState().getMode()); break; }
            if (previous.getExactSearchCriteria() != null) { state.setExact(previous.getExactSearchCriteria()); break; }
        }
        return state;
    }
    private StoneRagPreparedTurnEntity clarification(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, String reason) {
        StoneSemanticSearchResultEntity result = new StoneSemanticSearchResultEntity(); result.setState(previousState(history)); result.setText(reason + " Which stone do you mean?");
        return new StoneRagPreparedTurnEntity(components.mapping().turn().toEntity(new StoneRagAnswerAssemblyEntity(turn, result, null,
                new StoneRagTurnStateEntity(StoneRagMode.CLARIFY, StoneRagCompletionKind.CLARIFICATION, components.knowledge().references().selectionReferenceTurnId(history), false))), null);
    }
    private void checkWork(StoneRagExecutionEntity execution) { if (!timing.clock().instant().isBefore(execution.getModelDeadline())) throw new StoneChatUnavailableException(); }
    private void checkDeadline(StoneRagExecutionEntity execution) { if (!timing.clock().instant().isBefore(execution.getDeadline())) throw new StoneChatUnavailableException(); }
}
