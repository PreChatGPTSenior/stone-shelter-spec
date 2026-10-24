package lab.stoneshelter.services;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneExactSearchResultEntity;
import lab.stoneshelter.entities.StoneExactSearchPreparationEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.shared.StoneExactSearchDecision;
import lab.stoneshelter.enums.StoneExactSearchAction;
import lab.stoneshelter.enums.StoneExactSearchReferenceKind;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.mappers.entities.StoneExactSearchResultEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchDecisionToStoneExactSearchCriteriaMapper;
import lab.stoneshelter.repositories.StoneExactSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional
public class StoneExactSearchService {
    private record Mapping(StoneExactSearchResultEntityToStoneChatTurnEntityMapper result, StoneExactSearchDecisionToStoneExactSearchCriteriaMapper decision) {}
    private record Components(StoneExactSearchProvider provider, StoneExactSearchValidationService validation,
            StoneExactSearchRepository repository, Mapping mapping) {}
    private static final String MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december";
    private final Components components;
    private final Clock clock;
    public StoneExactSearchService(StoneExactSearchProvider provider, StoneExactSearchValidationService validation,
            StoneExactSearchRepository repository, StoneExactSearchResultEntityToStoneChatTurnEntityMapper mapper, StoneExactSearchDecisionToStoneExactSearchCriteriaMapper criteriaMapper, Clock clock) {
        components = new Components(provider, validation, repository, new Mapping(mapper, criteriaMapper)); this.clock = clock;
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneChatTurnEntity complete(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history, Instant deadline) {
        StoneExactSearchDecision decision = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String output = components.provider().invoke(turn, history, deadline,
                    attempt == 0 ? null : "Return exactly the required JSON fields, known enums, valid ranges and action.").text();
            try { decision = components.validation().validate(output); break; }
            catch (InvalidStoneExactSearchOutputException exception) { if (attempt == 1) throw exception; }
        }
        StoneExactSearchPreparationEntity preparation = prepare(turn, history, decision, false);
        return preparation.earlyTurn() != null ? preparation.earlyTurn() : search(turn, preparation.criteria());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneExactSearchPreparationEntity prepare(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history,
            StoneExactSearchDecision decision, boolean semanticNarrowing) {
        return prepare(turn, history, decision, semanticNarrowing, false);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneExactSearchPreparationEntity prepare(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history,
            StoneExactSearchDecision decision, boolean semanticNarrowing, boolean knownSelection) {
        StoneExactSearchCriteria prior = decision.getResetSearch() ? null : previousCriteria(history);
        StoneExactSearchCriteria effective = merge(decision, decision.getResetSearch() ? null : prior);
        String request = turn.getMessage().toLowerCase(Locale.ROOT);
        if (decision.getAction() == StoneExactSearchAction.UNSUPPORTED || !semanticNarrowing && !knownSelection && unsupported(request))
            return preparedAnswer(effective, answer(turn, decision.getResetSearch() ? null : prior,
                    "I can search by stone type, size, availability and admission date. Please choose one of these conditions."));
        if (missingYear(request)) {
            effective.setAdmissionDateRanges(prior == null ? List.of() : prior.getAdmissionDateRanges());
            return preparedAnswer(effective, clarify(turn, effective, "Which year do you mean?"));
        }
        if (decision.getAction() == StoneExactSearchAction.CLARIFY)
            return preparedAnswer(effective, answer(turn, effective, questions(decision.getQuestions())));
        try { effective.setAdmissionDateRanges(resolveDates(request, effective.getAdmissionDateRanges())); }
        catch (InvalidStoneExactSearchOutputException exception) { return preparedAnswer(effective, clarify(turn, prior, "Please specify a valid calendar date.")); }
        if (decision.getReference() != null) {
            Long id = null;
            if (decision.getReference().kind() == StoneExactSearchReferenceKind.PAGE_CONTEXT) {
                if (turn.getContext() != null) id = turn.getContext().stoneId();
            } else {
                for (int index = history.size() - 1; index >= 0; index--) {
                    var shown = history.get(index).getStones();
                    if (shown == null || shown.isEmpty()) continue;
                    int ordinal = decision.getReference().ordinal();
                    if (ordinal <= shown.size()) id = shown.get(ordinal - 1).id();
                    break;
                }
            }
            if (id == null || components.repository().findById(id).isEmpty())
                return preparedAnswer(effective, clarify(turn, prior, "That reference is ambiguous or the stone is no longer available."));
            effective.setStoneId(id);
        }
        if (!semanticNarrowing && !effective.hasNarrowing()) return preparedAnswer(effective, clarify(turn, prior, "Choose a narrowing condition before searching."));
        return new StoneExactSearchPreparationEntity(effective, null);
    }

    private StoneExactSearchPreparationEntity preparedAnswer(StoneExactSearchCriteria effective, StoneChatTurnEntity answer) {
        return new StoneExactSearchPreparationEntity(effective, answer);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneChatTurnEntity search(StoneChatTurnEntity turn, StoneExactSearchCriteria effective) {
        return search(turn, effective, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StoneChatTurnEntity search(StoneChatTurnEntity turn, StoneExactSearchCriteria effective, StoneSemanticSearchExecutionEntity execution) {
        StoneExactSearchResultEntity result = components.repository().search(effective);
        result.setTurn(turn);
        result.setText(result.getTotal() == 0 ? "No available stones match these conditions."
                : result.getTotal() > 5 ? "Found " + result.getTotal() + " matching available stones. Here are the first five. Which stone type would you prefer? What size would you like?"
                : "Found " + result.getTotal() + " matching available stones.");
        if (execution != null) execution.setExactResult(result);
        return components.mapping().result().toEntity(result);
    }
    @Transactional(readOnly = true)
    public long count(StoneExactSearchCriteria stoneExactSearchCriteria) { return components.repository().count(stoneExactSearchCriteria); }

    private StoneChatTurnEntity clarify(StoneChatTurnEntity turn, StoneExactSearchCriteria prior, String reason) {
        return answer(turn, prior, reason + " Which stone type would you like? What size or admission date do you prefer?");
    }
    private String questions(List<String> questions) {
        if (questions.size() >= 2 && questions.stream().distinct().count() >= 2
                && questions.stream().allMatch(question -> Pattern.compile("\\b(type|kind|size|date|admission)\\b")
                        .matcher(question.toLowerCase(Locale.ROOT)).find())) return String.join(" ", questions);
        return "Which stone type would you like? What size or admission date do you prefer?";
    }
    private StoneChatTurnEntity answer(StoneChatTurnEntity turn, StoneExactSearchCriteria criteria, String text) {
        StoneExactSearchResultEntity result = new StoneExactSearchResultEntity();
        result.setTurn(turn); result.setCriteria(criteria == null ? new StoneExactSearchCriteria() : criteria); result.setText(text);
        return components.mapping().result().toEntity(result);
    }
    private StoneExactSearchCriteria previousCriteria(List<StoneChatTurnEntity> history) {
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).getExactSearchCriteria() != null) return history.get(index).getExactSearchCriteria();
        }
        return null;
    }
    private StoneExactSearchCriteria merge(StoneExactSearchDecision decision, StoneExactSearchCriteria prior) {
        StoneExactSearchCriteria result = components.mapping().decision().toEntity(decision);
        if (prior != null) {
            if (result.getStoneTypes().isEmpty()) result.setStoneTypes(prior.getStoneTypes());
            if (result.getStoneSizes().isEmpty()) result.setStoneSizes(prior.getStoneSizes());
            if (result.getAvailability().isEmpty()) result.setAvailability(prior.getAvailability());
            if (result.getAdmissionDateRanges().isEmpty()) result.setAdmissionDateRanges(prior.getAdmissionDateRanges());
            result.setStoneId(prior.getStoneId());
        }
        return result;
    }
    private boolean unsupported(String request) {
        return Pattern.compile("\\b(colou?r|red|blue|green|black|white|pink|purple|yellow|care|geology|geological|gardening|garden|decoration|construction)\\b")
                .matcher(request).find();
    }
    private boolean missingYear(String request) {
        return Pattern.compile("\\b(" + MONTHS + ")\\b").matcher(request).find()
                && !Pattern.compile("\\b[0-9]{4}\\b").matcher(request).find();
    }
    private List<StoneExactSearchCriteria.DateRange> resolveDates(String request, List<StoneExactSearchCriteria.DateRange> interpreted) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (Pattern.compile("\\b(today|yesterday)\\b").matcher(request).find()) {
            List<StoneExactSearchCriteria.DateRange> result = new ArrayList<>();
            if (Pattern.compile("\\btoday\\b").matcher(request).find()) result.add(day(today));
            if (Pattern.compile("\\byesterday\\b").matcher(request).find()) result.add(day(today.minusDays(1)));
            return result;
        }
        Matcher month = Pattern.compile("\\b(" + MONTHS + ")\\b").matcher(request);
        Matcher year = Pattern.compile("\\b([0-9]{4})\\b").matcher(request);
        if (month.find() && year.find() && !request.matches("(?s).*\\b(or|between|from|until|through|before|after)\\b.*")) {
            int number = List.of(MONTHS.split("\\|")).indexOf(month.group()) + 1;
            Matcher before = Pattern.compile("\\b([0-9]{1,2})\\s+" + month.group() + "\\b").matcher(request);
            Matcher after = Pattern.compile("\\b" + month.group() + "\\s+([0-9]{1,2})(?:st|nd|rd|th)?\\b").matcher(request);
            int day = before.find() ? Integer.parseInt(before.group(1)) : after.find() ? Integer.parseInt(after.group(1)) : 0;
            try {
                LocalDate start = LocalDate.of(Integer.parseInt(year.group()), number, day == 0 ? 1 : day);
                return List.of(new StoneExactSearchCriteria.DateRange(start.atStartOfDay().toInstant(ZoneOffset.UTC),
                        (day == 0 ? start.plusMonths(1) : start.plusDays(1)).atStartOfDay().toInstant(ZoneOffset.UTC)));
            } catch (java.time.DateTimeException exception) { throw new InvalidStoneExactSearchOutputException(exception); }
        }
        return interpreted;
    }
    private StoneExactSearchCriteria.DateRange day(LocalDate date) {
        return new StoneExactSearchCriteria.DateRange(date.atStartOfDay().toInstant(ZoneOffset.UTC), date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
