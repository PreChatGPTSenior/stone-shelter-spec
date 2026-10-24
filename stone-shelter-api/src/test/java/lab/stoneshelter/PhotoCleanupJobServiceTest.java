package lab.stoneshelter;

import java.time.Duration;
import java.util.List;
import lab.stoneshelter.exceptions.PhotoStorageUnavailableException;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.services.PhotoCleanupJobService;
import lab.stoneshelter.services.PhotoCleanupService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoCleanupJobServiceTest {
    private final PhotoCleanupService cleanup = mock(PhotoCleanupService.class);
    private final MinioPhotoStorageService storage = mock(MinioPhotoStorageService.class);

    @Test
    void unavailableStorageDoesNotReadOrProcessTheQueue() {
        doThrow(new PhotoStorageUnavailableException()).when(storage).checkAvailability();
        new PhotoCleanupJobService(cleanup, storage, 100, 1000, 0, Duration.ofMinutes(30)).run();
        verifyNoInteractions(cleanup);
    }

    @Test
    void processesMultipleBatchesWithinTheObjectBudget() {
        when(cleanup.findDueKeys(2)).thenReturn(List.of("a", "b"));
        when(cleanup.findDueKeys(1)).thenReturn(List.of("c"));
        new PhotoCleanupJobService(cleanup, storage, 2, 3, 0, Duration.ofMinutes(30)).run();
        verify(cleanup).clean("a");
        verify(cleanup).clean("b");
        verify(cleanup).clean("c");
        verify(cleanup).findDueKeys(2);
        verify(cleanup).findDueKeys(1);
    }

    @Test
    void midRunFailureDoesNotAttemptRemainingObjects() {
        when(cleanup.findDueKeys(3)).thenReturn(List.of("a", "b", "c"));
        doThrow(new PhotoStorageUnavailableException()).when(cleanup).clean("b");
        new PhotoCleanupJobService(cleanup, storage, 3, 3, 0, Duration.ofMinutes(30)).run();
        verify(cleanup).clean("a");
        verify(cleanup).clean("b");
        verify(cleanup, never()).clean("c");
    }

    @Test
    void elapsedBudgetIncludesHealthCheckAndStopsBeforeQueueAccess() {
        doAnswer(invocation -> {
            Thread.sleep(10);
            return null;
        }).when(storage).checkAvailability();
        new PhotoCleanupJobService(cleanup, storage, 100, 1000, 0, Duration.ofNanos(1)).run();
        verifyNoInteractions(cleanup);
    }

    @Test
    void doesNotWaitOrStartTheNextIntentWhenPacingWouldExceedTheTimeBudget() {
        when(cleanup.findDueKeys(2)).thenReturn(List.of("a", "b"));
        new PhotoCleanupJobService(cleanup, storage, 2, 2, 2000, Duration.ofSeconds(1)).run();
        verify(cleanup).clean("a");
        verify(cleanup, never()).clean("b");
    }

    @Test
    void successfulRemovalsArePacedRatherThanIssuedAsABurst() {
        when(cleanup.findDueKeys(2)).thenReturn(List.of("a", "b"));
        var calls = new java.util.ArrayList<Long>();
        doAnswer(invocation -> {
            calls.add(System.nanoTime());
            return null;
        }).when(cleanup).clean(org.mockito.ArgumentMatchers.anyString());
        new PhotoCleanupJobService(cleanup, storage, 2, 2, 50, Duration.ofMinutes(30)).run();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1) - calls.get(0)).isGreaterThanOrEqualTo(Duration.ofMillis(50).toNanos());
    }

    @Test
    void permanentAndDraftWorkShareTheSameObjectBudget() {
        when(cleanup.findDueKeys(2)).thenReturn(List.of("permanent"), List.of());
        when(cleanup.findDueDraftKeys(2)).thenReturn(List.of("draft-a", "draft-b"));
        new PhotoCleanupJobService(cleanup, storage, 2, 3, 0, Duration.ofMinutes(30)).run();
        verify(cleanup).clean("permanent");
        verify(cleanup).cleanDraft("draft-a");
        verify(cleanup).cleanDraft("draft-b");
        verify(cleanup, never()).findDueDraftKeys(1);
    }

    @Test
    void draftFailureStopsTheRunWithoutAttemptingRemainingObjects() {
        when(cleanup.findDueDraftKeys(3)).thenReturn(List.of("a", "b", "c"));
        doThrow(new PhotoStorageUnavailableException()).when(cleanup).cleanDraft("b");
        new PhotoCleanupJobService(cleanup, storage, 3, 3, 0, Duration.ofMinutes(30)).run();
        verify(cleanup).cleanDraft("a");
        verify(cleanup).cleanDraft("b");
        verify(cleanup, never()).cleanDraft("c");
    }

    @Test
    void interruptedWorkerPreservesTheInterruptAndDoesNotContactDependencies() {
        try {
            Thread.currentThread().interrupt();
            new PhotoCleanupJobService(cleanup, storage, 100, 1000, 0, Duration.ofMinutes(30)).run();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(cleanup, storage);
        } finally {
            Thread.interrupted();
        }
    }
}
