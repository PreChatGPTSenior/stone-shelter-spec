package lab.stoneshelter;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatConversationEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneRagPreparedTurnEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneChatConversationEntityToStoneChatHistoryResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatTurnEntityToStoneChatMessageResponseMapper;
import lab.stoneshelter.mappers.entities.StoneChatMessageRequestToStoneChatTurnEntityMapper;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.services.StoneChatAdmissionService;
import lab.stoneshelter.services.StoneChatAvailabilityService;
import lab.stoneshelter.services.StoneChatbotService;
import lab.stoneshelter.services.StoneRagConversationService;
import lab.stoneshelter.services.StoneChatSettings;
import org.springframework.mock.env.MockEnvironment;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lab.stoneshelter.services.StoneChatContextSelector;
import lab.stoneshelter.services.StoneChatConversationSerializationService;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoneChatbotServiceTest {
    @Test void committedReplayUsesMapperAndNeverAcquiresProcessingPermitOrRefreshesCookie() {
        StoneChatConversationRepository history = mock(StoneChatConversationRepository.class);
        StoneChatConversationSerializationService serialization = mock(StoneChatConversationSerializationService.class);
        StoneChatAdmissionService admission = mock(StoneChatAdmissionService.class);
        StoneChatAvailabilityService availability = mock(StoneChatAvailabilityService.class);
        when(availability.execute(any())).thenAnswer(invocation -> invocation.<java.util.function.Supplier<?>>getArgument(0).get());
        StoneChatMessageRequest request = new StoneChatMessageRequest(UUID.randomUUID(), UUID.randomUUID(), "exact", null);
        StoneChatTurnEntity turn = new StoneChatMessageRequestToStoneChatTurnEntityMapper().toEntity(request);
        turn.setText("Here are some stones you might like."); turn.setStones(List.of(new StoneChatStoneEntity(1, "Mars"), new StoneChatStoneEntity(3, "Luna")));
        when(history.findById(anyString(), any())).thenReturn(new StoneChatConversationEntity(1, request.conversationId(), List.of(turn)));
        when(history.findCommitted(any(), any())).thenReturn(turn);
        Runnable committed = mock(Runnable.class);
        StoneChatbotService service = new StoneChatbotService(history, serialization, admission, availability,
                new StoneChatMessageRequestToStoneChatTurnEntityMapper(), new StoneChatTurnEntityToStoneChatMessageResponseMapper(),
                new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector());
        StoneRagConversationService exact = mock(StoneRagConversationService.class);
        when(exact.complete(any(), any(), any())).thenAnswer(invocation -> {
            StoneChatTurnEntity completed = invocation.getArgument(0); completed.setText("Exact catalog result");
            completed.setStones(List.of(new StoneChatStoneEntity(42, "Real catalog stone"))); return new StoneRagPreparedTurnEntity(completed, null);
        });
        service.configureRagSearch(exact, Clock.systemUTC(), new StoneChatSettings(new MockEnvironment()));
        assertThat(service.sendMessage(request, "session", committed).stones()).hasSize(2);
        verify(exact, never()).complete(any(), any(), any()); verify(serialization, never()).acquire(anyString(), any()); verify(committed, never()).run();
        when(history.findCommitted(any(), any())).thenReturn(null);
        var guard = mock(StoneChatConversationSerializationService.Guard.class);
        when(serialization.acquire(anyString(), any())).thenReturn(guard); when(guard.token()).thenReturn("token");
        when(history.append(anyString(), any(), anyString(), any())).thenThrow(new StoneChatUnavailableException());
        assertThatThrownBy(() -> service.sendMessage(request, "session", committed)).isInstanceOf(StoneChatUnavailableException.class);
        verify(guard, times(1)).close(); verify(committed, never()).run();
        doThrow(new StoneChatUnavailableException()).when(admission).check("session", request.conversationId(), true);
        assertThatThrownBy(() -> service.sendMessage(request, "session", committed)).isInstanceOf(StoneChatUnavailableException.class);
        org.mockito.Mockito.doNothing().when(admission).check("session", request.conversationId(), true);
        doAnswer(invocation -> invocation.getArgument(3)).when(history).append(anyString(), any(), anyString(), any());
        StoneChatMessageRequest newRequest = new StoneChatMessageRequest(request.conversationId(), UUID.randomUUID(), "new turn", null);
        assertThat(service.sendMessage(newRequest, "session", committed).text()).isEqualTo("Exact catalog result");
        verify(committed, times(1)).run();
    }
    @Test void invalidExactTurnIsNotAppendedAndDoesNotRefreshRetention() {
        StoneChatConversationRepository history = mock(StoneChatConversationRepository.class);
        StoneChatConversationSerializationService serialization = mock(StoneChatConversationSerializationService.class);
        StoneChatAvailabilityService availability = mock(StoneChatAvailabilityService.class);
        when(availability.execute(any())).thenAnswer(call -> call.<java.util.function.Supplier<?>>getArgument(0).get());
        UUID conversationId = UUID.randomUUID();
        when(history.findById(anyString(), any())).thenReturn(new StoneChatConversationEntity(1, conversationId, List.of()));
        var guard = mock(StoneChatConversationSerializationService.Guard.class);
        when(serialization.acquire(anyString(), any())).thenReturn(guard);
        StoneRagConversationService exact = mock(StoneRagConversationService.class);
        when(exact.complete(any(), any(), any())).thenThrow(new lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException());
        StoneChatbotService service = new StoneChatbotService(history, serialization, mock(StoneChatAdmissionService.class), availability,
                new StoneChatMessageRequestToStoneChatTurnEntityMapper(), new StoneChatTurnEntityToStoneChatMessageResponseMapper(),
                new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector());
        service.configureRagSearch(exact, Clock.systemUTC(), new StoneChatSettings(new MockEnvironment()));
        Runnable committed = mock(Runnable.class);
        assertThatThrownBy(() -> service.sendMessage(new StoneChatMessageRequest(conversationId, UUID.randomUUID(), "basalt", null), "session", committed))
                .isInstanceOf(lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException.class);
        verify(history, never()).append(anyString(), any(), anyString(), any()); verify(committed, never()).run(); verify(guard).close();
    }

    @Test void entireTurnDeadlineRetainsLiveLeaseUntilActualExitAndPreventsLatePersistence() throws Exception {
        var history = mock(StoneChatConversationRepository.class); var serialization = mock(StoneChatConversationSerializationService.class);
        var availability = mock(StoneChatAvailabilityService.class);
        when(availability.execute(any())).thenAnswer(call -> call.<java.util.function.Supplier<?>>getArgument(0).get());
        UUID conversationId = UUID.randomUUID(); when(history.findById(anyString(), any())).thenReturn(new StoneChatConversationEntity(1, conversationId, List.of()));
        var guard = mock(StoneChatConversationSerializationService.Guard.class); AtomicBoolean closed = new AtomicBoolean();
        doAnswer(call -> { closed.set(true); return null; }).when(guard).close();
        doAnswer(call -> { if (closed.get()) throw new StoneChatUnavailableException(); return null; }).when(guard).check();
        when(serialization.acquire(anyString(), any())).thenReturn(guard);
        var exact = mock(StoneRagConversationService.class); CountDownLatch entered = new CountDownLatch(1); CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        when(exact.complete(any(), any(), any())).thenAnswer(call -> {
            worker.set(Thread.currentThread()); entered.countDown();
            while (finish.getCount() != 0) { try { finish.await(); } catch (InterruptedException ignored) { /* Still executing after cancellation request. */ } }
            return new StoneRagPreparedTurnEntity(call.getArgument(0), null);
        });
        var service = new StoneChatbotService(history, serialization, mock(StoneChatAdmissionService.class), availability,
                new StoneChatMessageRequestToStoneChatTurnEntityMapper(), new StoneChatTurnEntityToStoneChatMessageResponseMapper(),
                new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector());
        var settings = mock(StoneChatSettings.class); when(settings.deadline()).thenReturn(Duration.ofMillis(100)); service.configureRagSearch(exact, Clock.systemUTC(), settings);
        Runnable committed = mock(Runnable.class);
        try {
            assertThatThrownBy(() -> service.sendMessage(new StoneChatMessageRequest(conversationId, UUID.randomUUID(), "basalt", null), "session", committed))
                    .isInstanceOf(StoneChatUnavailableException.class);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue(); assertThat(closed.get()).isFalse();
        } finally { finish.countDown(); if (worker.get() != null) worker.get().join(2000); }
        assertThat(closed.get()).isTrue();
        verify(history, never()).append(anyString(), any(), anyString(), any()); verify(committed, never()).run();
    }

}
