package lab.stoneshelter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.mappers.entities.StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class ModelEmbeddingAdmissionTest {
    private final Clock clock = Clock.systemUTC();
    private final ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
    private final OllamaEmbeddingRepository repository = mock(OllamaEmbeddingRepository.class);
    private ModelInvocationAdmissionService guard() {
        when(ledger.reserve(any(), any(), anyLong(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), LocalDate.now(clock), 32768, call.getArgument(1)));
        return new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), clock);
    }
    private StoneDocumentEmbeddingService service(ModelInvocationAdmissionService guard) {
        var service = new StoneDocumentEmbeddingService(repository, new StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper(
                new StoneDocumentIndexSettings(new MockEnvironment().withProperty("STONE_DOCUMENT_OLLAMA_BASE_URL", "http://fixture.invalid")), clock));
        service.configureAdmission(guard, clock); return service;
    }
    @Test void embeddingUsesSameSlotAsChatAndBackgroundContentionNeverCallsProvider() {
        var guard = guard(); var service = service(guard);
        try (var lease = guard.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) {
            assertThatThrownBy(() -> service.embed(new StoneDocumentEmbeddingCriteria("Basalt", false, clock.instant().plusSeconds(2), ModelAdmissionMode.BACKGROUND)))
                    .isInstanceOf(ModelAdmissionDeferredException.class);
            verify(repository, never()).embed(any(), any());
        }
    }
    @Test void fullProfileReservationSettlesReportedInputIncludingInvalidOutput() {
        var service = service(guard());
        when(repository.embed(any(), any())).thenAnswer(call -> {
            LongConsumer usage = call.getArgument(1); usage.accept(16);
            throw new StoneDocumentDependencyUnavailableException("Invalid vector after reliable usage");
        });
        assertThatThrownBy(() -> service.embed(new StoneDocumentEmbeddingCriteria("Basalt", false, clock.instant().plusSeconds(2))))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        verify(ledger).reserve(any(), any(), org.mockito.ArgumentMatchers.eq(32768L), org.mockito.ArgumentMatchers.eq(100000L));
        verify(ledger).settle(any(), org.mockito.ArgumentMatchers.eq(16L));
    }
    @Test void unknownProviderUsageIsNotRefunded() {
        var service = service(guard()); when(repository.embed(any(), any())).thenThrow(new StoneDocumentDependencyUnavailableException("timeout"));
        assertThatThrownBy(() -> service.embed(new StoneDocumentEmbeddingCriteria("Basalt", false, clock.instant().plusSeconds(2))))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        verify(ledger, never()).settle(any(), anyLong());
    }
    @Test void importAdmissionDeadlineExpiresWithoutCallingProviderOrPartialWork() {
        var guard = guard(); var service = service(guard);
        try (var lease = guard.acquire(ModelAdmissionMode.CUSTOMER, 8192, clock.instant().plusSeconds(2))) {
            assertThatThrownBy(() -> service.embed(new StoneDocumentEmbeddingCriteria("Basalt", false, clock.instant().plusMillis(50), ModelAdmissionMode.IMPORT)))
                    .isInstanceOf(ModelAdmissionDeferredException.class);
            verify(repository, never()).embed(any(), any());
        }
    }
}
