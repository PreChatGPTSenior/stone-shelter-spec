package lab.stoneshelter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDate;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.services.OllamaExactSearchProvider;
import lab.stoneshelter.services.StoneExactSearchSettings;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import org.springframework.mock.env.MockEnvironment;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneChatContextEntity;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneExactSearchResultEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.repositories.StoneExactSearchRepository;
import lab.stoneshelter.mappers.entities.StoneExactSearchResultEntityToStoneChatTurnEntityMapper;
import lab.stoneshelter.mappers.entities.StoneExactSearchDecisionToStoneExactSearchCriteriaMapper;
import lab.stoneshelter.services.StoneExactSearchService;
import lab.stoneshelter.services.StoneExactSearchProvider;
import lab.stoneshelter.services.StoneExactSearchValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class StoneExactSearchServiceTest {
    private final StoneExactSearchProvider provider = mock(StoneExactSearchProvider.class);
    private final StoneExactSearchRepository repository = mock(StoneExactSearchRepository.class);
    private final Instant now = Instant.parse("2026-10-12T23:00:00Z");
    private final StoneExactSearchService service = new StoneExactSearchService(provider, new StoneExactSearchValidationService(),
            repository, new StoneExactSearchResultEntityToStoneChatTurnEntityMapper(), new StoneExactSearchDecisionToStoneExactSearchCriteriaMapper(), Clock.fixed(now, ZoneOffset.UTC));
    private StoneChatTurnEntity turn(String message) { var turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage(message); return turn; }
    private void output(String value) { when(provider.invoke(any(), any(), any(), any())).thenReturn(new StoneModelResponseEntity(value, 20, 20)); }
    private String decision(String types, String sizes, String dates, String reference, boolean reset) {
        return "{\"action\":\"SEARCH\",\"criteria\":{\"stoneTypes\":" + types + ",\"stoneSizes\":" + sizes
                + ",\"availability\":[],\"admissionDateRanges\":" + dates + "},\"resetSearch\":" + reset
                + ",\"reference\":" + reference + ",\"questions\":[],\"reason\":\"\"}";
    }
    private void matches(long total) {
        when(repository.search(any())).thenAnswer(call -> {
            var result = new StoneExactSearchResultEntity(); result.setCriteria(call.getArgument(0)); result.setTotal(total);
            if (total > 0) { StoneEntity stone = new StoneEntity(); stone.setId(42L); stone.setName("Real catalog name"); result.setStones(List.of(stone)); }
            return result;
        });
    }
    private StoneExactSearchCriteria captured() { var capture = ArgumentCaptor.forClass(StoneExactSearchCriteria.class); verify(repository).search(capture.capture()); return capture.getValue(); }
    @Test void noNarrowingNeverQueriesAndAsksAtLeastTwoQuestions() {
        output(decision("[]", "[]", "[]", "null", false));
        var result = service.complete(turn("Show all available stones."), List.of(), now.plusSeconds(120));
        assertThat(result.getStones()).isEmpty(); assertThat(result.getText()).contains("Which stone type", "What size"); verifyNoInteractions(repository);
    }
    @Test void selectingEveryTypeAndSizeDoesNotEnableAnUnfilteredQuery() {
        String types = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(StoneType.values());
        String sizes = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(StoneSize.values());
        output(decision(types, sizes, "[]", "null", true));
        assertThat(service.complete(turn("show all available stones of every type and size"), List.of(), now.plusSeconds(120)).getStones()).isEmpty();
        verifyNoInteractions(repository);
    }
    @Test void exactResultsUseRealReferencesTrueCountAndRefinement() {
        output(decision("[\"BASALT\",\"GRANITE\"]", "[\"SMALL\"]", "[]", "null", false)); matches(9);
        var result = service.complete(turn("Find small basalt or granite stones"), List.of(), now.plusSeconds(120));
        assertThat(result.getText()).contains("9", "first five", "What size");
        assertThat(result.getStones()).containsExactly(new StoneChatStoneEntity(42, "Real catalog name"));
        assertThat(captured().getStoneTypes()).containsExactly(StoneType.BASALT, StoneType.GRANITE);
    }
    @Test void zeroResultsUseOnlyAbsenceWording() {
        output(decision("[\"BASALT\"]", "[]", "[]", "null", false)); matches(0);
        var result = service.complete(turn("basalt"), List.of(), now.plusSeconds(120));
        assertThat(result.getText()).isEqualTo("No available stones match these conditions."); assertThat(result.getStones()).isEmpty();
    }
    @Test void followupReplacesSizeAndRetainsTypeAndNewSearchClearsType() {
        var previous = turn("basalt"); var prior = new StoneExactSearchCriteria(); prior.setStoneTypes(List.of(StoneType.BASALT)); prior.setStoneSizes(List.of(StoneSize.LARGE)); previous.setExactSearchCriteria(prior);
        output(decision("[]", "[\"SMALL\"]", "[]", "null", false)); matches(1);
        var result = service.complete(turn("now only small ones"), List.of(previous), now.plusSeconds(120));
        assertThat(result.getExactSearchCriteria().getStoneTypes()).containsExactly(StoneType.BASALT);
        assertThat(result.getExactSearchCriteria().getStoneSizes()).containsExactly(StoneSize.SMALL);
        output(decision("[]", "[\"SMALL\"]", "[]", "null", true));
        assertThat(service.complete(turn("start a new search for small stones"), List.of(previous), now.plusSeconds(120)).getExactSearchCriteria().getStoneTypes()).isEmpty();
    }
    @Test void clarificationRetainsValidatedPartialCriteriaForFollowup() {
        output(decision("[\"BASALT\"]", "[]", "[]", "null", false).replace("\"SEARCH\"", "\"CLARIFY\"")
                .replace("\"questions\":[]", "\"questions\":[\"What size would you prefer?\",\"What admission date would you prefer?\"]"));
        var previous = service.complete(turn("I prefer basalt; help me choose a size"), List.of(), now.plusSeconds(120));
        assertThat(previous.getExactSearchCriteria().getStoneTypes()).containsExactly(StoneType.BASALT); verifyNoInteractions(repository);
        output(decision("[]", "[\"SMALL\"]", "[]", "null", false)); matches(1);
        assertThat(service.complete(turn("small ones"), List.of(previous), now.plusSeconds(120)).getExactSearchCriteria().getStoneTypes()).containsExactly(StoneType.BASALT);
    }
    @Test void relativeDatesAreResolvedFromUtcClockInsteadOfModelGuesses() {
        output(decision("[]", "[]", "[]", "null", false)); matches(1);
        service.complete(turn("stones admitted yesterday or today"), List.of(), now.plusSeconds(120));
        assertThat(captured().getAdmissionDateRanges()).containsExactly(
                new StoneExactSearchCriteria.DateRange(Instant.parse("2026-10-12T00:00:00Z"), Instant.parse("2026-10-13T00:00:00Z")),
                new StoneExactSearchCriteria.DateRange(Instant.parse("2026-10-11T00:00:00Z"), Instant.parse("2026-10-12T00:00:00Z")));
    }
    @Test void missingCalendarYearClarifiesWithoutQuery() {
        output(decision("[]", "[]", "[]", "null", false));
        assertThat(service.complete(turn("stones admitted 1 October"), List.of(), now.plusSeconds(120)).getText()).contains("Which year"); verifyNoInteractions(repository);
    }
    @Test void calendarDayAndMonthUseExclusiveUtcEnds() {
        output(decision("[]", "[]", "[]", "null", false)); matches(1);
        assertThat(service.complete(turn("stones admitted 1 October 2026"), List.of(), now.plusSeconds(120)).getExactSearchCriteria().getAdmissionDateRanges())
                .containsExactly(new StoneExactSearchCriteria.DateRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z")));
        assertThat(service.complete(turn("stones admitted October 2026"), List.of(), now.plusSeconds(120)).getExactSearchCriteria().getAdmissionDateRanges())
                .containsExactly(new StoneExactSearchCriteria.DateRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z")));
    }
    @Test void unsupportedColorCannotBeSilentlyDroppedEvenWithValidType() {
        output(decision("[\"BASALT\"]", "[]", "[]", "null", false));
        var result = service.complete(turn("find blue basalt stones"), List.of(), now.plusSeconds(120));
        assertThat(result.getText()).contains("stone type, size, availability and admission date"); assertThat(result.getStones()).isEmpty(); verifyNoInteractions(repository);
    }
    @Test void ordinalUsesLatestNonemptyShownListAndRechecksAvailability() {
        var previous = turn("earlier"); previous.setStones(List.of(new StoneChatStoneEntity(11, "first"), new StoneChatStoneEntity(22, "second")));
        var empty = turn("clarification"); empty.setStones(List.of()); output(decision("[]", "[]", "[]", "{\"kind\":\"ORDINAL\",\"ordinal\":2}", false));
        when(repository.findById(22)).thenReturn(Optional.of(new StoneEntity())); matches(1);
        service.complete(turn("the second stone"), List.of(previous, empty), now.plusSeconds(120));
        assertThat(captured().getStoneId()).isEqualTo(22L);
    }
    @Test void missingOrUnavailableReferenceClarifiesWithoutSubstitution() {
        output(decision("[]", "[]", "[]", "{\"kind\":\"PAGE_CONTEXT\",\"ordinal\":null}", false));
        var current = turn("this stone"); current.setContext(new StoneChatContextEntity(99L)); when(repository.findById(99)).thenReturn(Optional.empty());
        assertThat(service.complete(current, List.of(), now.plusSeconds(120)).getText()).contains("no longer available");
        verify(repository).findById(99); verify(repository, times(0)).search(any());
    }
    @Test void invalidOutputGetsExactlyOneRepairAndStillInvalidFails() {
        when(provider.invoke(any(), any(), any(), any())).thenReturn(new StoneModelResponseEntity("not json", 1, 1), new StoneModelResponseEntity("{}", 1, 1));
        assertThatThrownBy(() -> service.complete(turn("basalt"), List.of(), now.plusSeconds(120))).isInstanceOf(InvalidStoneExactSearchOutputException.class);
        verify(provider, times(2)).invoke(any(), any(), any(), any()); verifyNoInteractions(repository);
    }
    @Test void actualProviderTimeoutKeepsPermitUntilWorkerExitsAndSettlesLateKnownUsage() throws Exception {
        Clock liveClock = Clock.systemUTC(); ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
        when(ledger.reserve(any(), any(), anyLong(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), LocalDate.now(liveClock), 8192, call.getArgument(1)));
        var guard = new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), liveClock);
        OllamaChatRepository model = mock(OllamaChatRepository.class); CountDownLatch entered = new CountDownLatch(1); CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        when(model.invoke(any())).thenAnswer(call -> {
            worker.set(Thread.currentThread()); entered.countDown();
            while (finish.getCount() != 0) { try { finish.await(); } catch (InterruptedException ignored) { /* Simulate a provider that has not cancelled yet. */ } }
            return new StoneModelResponseEntity("{}", 5, 3);
        });
        var actual = new OllamaExactSearchProvider(model, new StoneExactSearchSettings(new MockEnvironment()
                .withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://fixture.invalid").withProperty("CHAT_MODEL_CALL_TIMEOUT", "PT0.05S")), liveClock);
        actual.configureAdmission(guard);
        try {
            assertThatThrownBy(() -> actual.invoke(turn("basalt"), List.of(), liveClock.instant().plusSeconds(120), null)).isInstanceOf(StoneChatUnavailableException.class);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> guard.acquire(ModelAdmissionMode.CUSTOMER, 8192, liveClock.instant().plusSeconds(2))).isInstanceOf(ModelCapacityExceededException.class);
        } finally { finish.countDown(); if (worker.get() != null) worker.get().join(2000); }
        verify(ledger).settle(any(), eq(8L));
        try (var next = guard.acquire(ModelAdmissionMode.CUSTOMER, 8192, liveClock.instant().plusSeconds(2))) { assertThat(next.id()).isNotNull(); }
    }
    @Test void invalidCalendarDateAndGenericClarificationNeverExecuteCatalogQuery() {
        output(decision("[]", "[]", "[]", "null", false));
        assertThat(service.complete(turn("31 November 2026"), List.of(), now.plusSeconds(120)).getText()).contains("valid calendar date");
        output(decision("[]", "[]", "[]", "null", false).replace("\"SEARCH\"", "\"CLARIFY\"")
                .replace("\"questions\":[]", "\"questions\":[\"Could you clarify?\",\"Can you explain?\"]"));
        assertThat(service.complete(turn("some stones"), List.of(), now.plusSeconds(120)).getText()).contains("Which stone type", "What size");
        verifyNoInteractions(repository);
    }

    @Test void successfulRepairCanSearchAndStrictParserRejectsHiddenFieldsAndTrailingJson() {
        when(provider.invoke(any(), any(), any(), any())).thenReturn(new StoneModelResponseEntity("{}", 1, 1),
                new StoneModelResponseEntity(decision("[\"BASALT\"]", "[]", "[]", "null", false), 1, 1)); matches(1);
        assertThat(service.complete(turn("basalt"), List.of(), now.plusSeconds(120)).getStones()).hasSize(1);
        String valid = decision("[]", "[]", "[]", "null", false); var validation = new StoneExactSearchValidationService();
        assertThatThrownBy(() -> validation.validate(valid + "{}" )).isInstanceOf(InvalidStoneExactSearchOutputException.class);
        assertThatThrownBy(() -> validation.validate(valid.replace("\"action\":", "\"extra\":1,\"action\":"))).isInstanceOf(InvalidStoneExactSearchOutputException.class);
    }
}
