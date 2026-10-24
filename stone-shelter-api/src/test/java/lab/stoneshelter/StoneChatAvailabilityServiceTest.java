package lab.stoneshelter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import lab.stoneshelter.services.StoneChatAvailabilityService;
import lab.stoneshelter.services.StoneChatContextSelector;
import lab.stoneshelter.services.StoneExactSearchProvider;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import java.time.Instant;
import java.time.Clock;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import org.springframework.mock.env.MockEnvironment;
import lab.stoneshelter.services.StoneChatConversationSerializationService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class StoneChatAvailabilityServiceTest {
    @Test
    void componentFailureClosesAllOperationsButQuotaDoesNotInvalidateReadiness() {
        StoneChatAvailabilityService service = service(mock(StoneChatRateLimiterRepository.class));
        assertThatThrownBy(service::assertReady).isInstanceOf(StoneChatUnavailableException.class);
        service.recover(); service.assertReady();
        assertThatThrownBy(() -> service.execute(() -> { throw new StoneChatRateLimitedException(1); }))
                .isInstanceOf(StoneChatRateLimitedException.class);
        service.assertReady();
        assertThatThrownBy(() -> service.execute(() -> { throw new IllegalStateException("component unreadable"); }))
                .isInstanceOf(StoneChatUnavailableException.class);
        assertThatThrownBy(service::assertReady).isInstanceOf(StoneChatUnavailableException.class);
        service.ensureReady(true); service.assertReady();
    }
    @Test
    void concurrentRecoveryIsRejectedAndOldProbeCannotOverwriteNewFault() throws Exception {
        StoneChatRateLimiterRepository limiter = mock(StoneChatRateLimiterRepository.class);
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); release.await(); return null; }).when(limiter).probe(anyString());
        StoneChatAvailabilityService service = service(limiter);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var attempt = executor.submit(service::recover);
            entered.await();
            assertThatThrownBy(service::recover).isInstanceOf(StoneChatUnavailableException.class);
            service.invalidate(new IllegalStateException("new fault")); release.countDown();
            assertThatThrownBy(attempt::get).hasCauseInstanceOf(StoneChatUnavailableException.class);
            assertThatThrownBy(service::assertReady).isInstanceOf(StoneChatUnavailableException.class);
        } finally { release.countDown(); }
    }
    @Test
    void everyRequiredProbeFailureKeepsChatClosedAndStartupDoesNotFailApplicationLiveness() {
        for (int component = 0; component < 6; component++) {
            StoneChatSessionRepository sessions = mock(StoneChatSessionRepository.class);
            StoneChatConversationRepository history = mock(StoneChatConversationRepository.class);
            StoneChatRateLimiterRepository limiter = mock(StoneChatRateLimiterRepository.class);
            StoneChatActiveTurnRepository leases = mock(StoneChatActiveTurnRepository.class);
            StoneChatRedisRepository redis = mock(StoneChatRedisRepository.class);
            StoneChatConversationSerializationService serialization = mock(StoneChatConversationSerializationService.class);
            switch (component) {
                case 0 -> doThrow(new IllegalStateException("session")).when(sessions).probe(anyString());
                case 1 -> doThrow(new IllegalStateException("history")).when(history).probe(anyString());
                case 2 -> doThrow(new IllegalStateException("limiter")).when(limiter).probe(anyString());
                case 3 -> doThrow(new IllegalStateException("lease")).when(leases).probe(anyString());
                case 4 -> doThrow(new IllegalStateException("Redis")).when(redis).probe(anyString());
                case 5 -> doThrow(new IllegalStateException("serialization")).when(serialization).probe();
                default -> throw new AssertionError();
            }
            StoneChatAvailabilityService service = new StoneChatAvailabilityService(sessions, history, limiter, leases,
                    redis, serialization, new StoneChatContextSelector());
            service.configureModels(mock(StoneExactSearchProvider.class), mock(ModelInvocationAdmissionService.class));
            service.initialize();
            assertThatThrownBy(() -> service.ensureReady(false)).isInstanceOf(StoneChatUnavailableException.class);
            assertThatThrownBy(service::recover).isInstanceOf(StoneChatUnavailableException.class);
        }
    }
    @Test
    void healthyModelRejectionsDoNotCloseHistoryAndReplayButActualProviderAndLedgerFailuresDo() {
        StoneChatAvailabilityService service = service(mock(StoneChatRateLimiterRepository.class));
        StoneExactSearchProvider provider = mock(StoneExactSearchProvider.class);
        ModelInvocationAdmissionService admission = mock(ModelInvocationAdmissionService.class);
        service.configureModels(provider, admission); service.recover();
        for (RuntimeException failure : java.util.List.of(new ModelCapacityExceededException(),
                new ModelTokenBudgetExceededException(Instant.now().plusSeconds(100)), new InvalidStoneExactSearchOutputException())) {
            assertThatThrownBy(() -> service.execute(() -> { throw failure; })).isSameAs(failure); service.assertReady();
        }
        doThrow(new IllegalStateException("Ollama unavailable")).when(provider).probe();
        assertThatThrownBy(service::recover).isInstanceOf(StoneChatUnavailableException.class);
        assertThatThrownBy(service::assertReady).isInstanceOf(StoneChatUnavailableException.class);
        org.mockito.Mockito.doNothing().when(provider).probe(); service.recover(); service.assertReady();
        doThrow(new IllegalStateException("Ledger unavailable")).when(admission).probe();
        assertThatThrownBy(service::recover).isInstanceOf(StoneChatUnavailableException.class);
        assertThatThrownBy(service::assertReady).isInstanceOf(StoneChatUnavailableException.class);
    }

    @Test void backgroundModelFailureClosesSharedChatUntilVerifiedManualRecovery() {
        var admission = new ModelInvocationAdmissionService(mock(ModelTokenBudgetTransactionService.class), new ModelInvocationSettings(new MockEnvironment()), Clock.systemUTC());
        var service = service(mock(StoneChatRateLimiterRepository.class));
        service.configureModels(mock(StoneExactSearchProvider.class), admission); service.recover(); service.assertReady();
        admission.failure(new IllegalStateException("background model dependency failed"));
        assertThatThrownBy(service::assertReady).isInstanceOf(StoneChatUnavailableException.class);
        service.ensureReady(true); service.assertReady();
    }

    private static StoneChatAvailabilityService service(StoneChatRateLimiterRepository limiter) {
        StoneChatAvailabilityService service = new StoneChatAvailabilityService(mock(StoneChatSessionRepository.class), mock(StoneChatConversationRepository.class),
                limiter, mock(StoneChatActiveTurnRepository.class), mock(StoneChatRedisRepository.class),
                mock(StoneChatConversationSerializationService.class), new StoneChatContextSelector());
        service.configureModels(mock(StoneExactSearchProvider.class), mock(ModelInvocationAdmissionService.class));
        return service;
    }
}
