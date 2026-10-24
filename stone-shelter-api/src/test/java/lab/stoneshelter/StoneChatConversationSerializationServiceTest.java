package lab.stoneshelter;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.exceptions.StoneChatConversationBusyException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.services.StoneChatConversationSerializationService;
import lab.stoneshelter.services.StoneChatSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoneChatConversationSerializationServiceTest {
    @Test
    void failedRenewalStopsProgressWithoutReleasingTheLivePermit() throws Exception {
        StoneChatActiveTurnRepository stoneChatActiveTurnRepository = mock(StoneChatActiveTurnRepository.class);
        CountDownLatch failureObserved = new CountDownLatch(1);
        when(stoneChatActiveTurnRepository.acquire(anyString(), any(), anyString())).thenReturn(true);
        when(stoneChatActiveTurnRepository.renew(anyString(), anyString())).thenReturn(false);
        when(stoneChatActiveTurnRepository.unavailable(any())).thenAnswer(invocation -> {
            failureObserved.countDown();
            return new StoneChatUnavailableException(invocation.getArgument(0));
        });
        StoneChatConversationSerializationService stoneChatConversationSerializationService = new StoneChatConversationSerializationService(
                stoneChatActiveTurnRepository, new StoneChatSettings(new MockEnvironment().withProperty("CHAT_TURN_RENEWAL", "PT0.01S")));
        UUID conversationId = UUID.randomUUID();
        try (var guard = stoneChatConversationSerializationService.acquire("session", conversationId)) {
            assertThat(failureObserved.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(guard::check).isInstanceOf(StoneChatUnavailableException.class);
            assertThatThrownBy(() -> stoneChatConversationSerializationService.acquire("session", conversationId))
                    .isInstanceOf(StoneChatConversationBusyException.class);
        } finally { stoneChatConversationSerializationService.close(); }
    }
    @Test
    void sameConversationKeepsPermitUntilActualExitWhileOtherConversationsProceed() {
        StoneChatActiveTurnRepository repository = mock(StoneChatActiveTurnRepository.class);
        when(repository.acquire(anyString(), any(), anyString())).thenReturn(true);
        when(repository.unavailable(any())).thenAnswer(invocation -> new StoneChatUnavailableException(invocation.getArgument(0)));
        MockEnvironment environment = new MockEnvironment().withProperty("CHAT_TURN_DEADLINE", "PT0.01S");
        StoneChatConversationSerializationService service = new StoneChatConversationSerializationService(repository, new StoneChatSettings(environment));
        UUID conversationId = UUID.randomUUID();
        try (var first = service.acquire("session", conversationId)) {
            assertThatThrownBy(() -> service.acquire("session", conversationId)).isInstanceOf(StoneChatConversationBusyException.class);
            try (var other = service.acquire("session", UUID.randomUUID())) { other.check(); }
            try { Thread.sleep(20); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            assertThatThrownBy(first::check).isInstanceOf(StoneChatUnavailableException.class);
            assertThatThrownBy(() -> service.acquire("session", conversationId)).isInstanceOf(StoneChatConversationBusyException.class);
        } finally { service.close(); }
    }
    @Test
    void successfulCompletionSafelyRetiresAndRecreatesIdleEntry() {
        StoneChatActiveTurnRepository repository = mock(StoneChatActiveTurnRepository.class);
        when(repository.acquire(anyString(), any(), anyString())).thenReturn(true);
        StoneChatConversationSerializationService service = new StoneChatConversationSerializationService(repository, new StoneChatSettings(new MockEnvironment()));
        UUID conversationId = UUID.randomUUID();
        try {
            for (int i = 0; i < 20; i++) try (var guard = service.acquire("session", conversationId)) { guard.check(); }
            service.probe();
        } finally { service.close(); }
    }
}
