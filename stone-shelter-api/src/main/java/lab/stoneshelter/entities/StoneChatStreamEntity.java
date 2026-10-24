package lab.stoneshelter.entities;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import lab.stoneshelter.enums.StoneChatStreamEventType;

/** Private delivery cursor over one confirmed immutable snapshot; never persisted as history. */
public class StoneChatStreamEntity {
    public record SavedResult(String text, List<StoneChatStoneEntity> stones, long confirmedAtNanos) {
        public SavedResult { stones = List.copyOf(stones); }
    }
    private StoneChatAdmittedTurnEntity admittedTurn;
    private CompletableFuture<SavedResult> completion = new CompletableFuture<>();
    private LongSupplier nanoClock = System::nanoTime;
    private Runnable releaseTransport;
    private AtomicBoolean transportExited = new AtomicBoolean();
    private long sequence;
    private int textOffset;
    private boolean started;
    private boolean terminal;
    public StoneChatStreamEntity() {}
    public StoneChatAdmittedTurnEntity getAdmittedTurn() { return admittedTurn; }
    public void setAdmittedTurn(StoneChatAdmittedTurnEntity value) { admittedTurn = value; }
    public CompletableFuture<SavedResult> getCompletion() { return completion; }
    public void setCompletion(CompletableFuture<SavedResult> value) { completion = value; }
    public LongSupplier getNanoClock() { return nanoClock; }
    public void setNanoClock(LongSupplier value) { nanoClock = value; }
    public Runnable getReleaseTransport() { return releaseTransport; }
    public void setReleaseTransport(Runnable value) { releaseTransport = value; }
    public AtomicBoolean getTransportExited() { return transportExited; }
    public void setTransportExited(AtomicBoolean value) { transportExited = value; }
    public long getSequence() { return sequence; }
    public void setSequence(long value) { sequence = value; }
    public int getTextOffset() { return textOffset; }
    public void setTextOffset(int value) { textOffset = value; }
    public boolean getStarted() { return started; }
    public void setStarted(boolean value) { started = value; }
    public boolean getTerminal() { return terminal; }
    public void setTerminal(boolean value) { terminal = value; }
    public synchronized boolean confirm(StoneChatTurnEntity saved) {
        if (saved == null || !admittedTurn.getRequestTurn().getTurnId().equals(saved.getTurnId()) || saved.getText() == null || saved.getStones() == null)
            throw new IllegalArgumentException("A confirmed result must match the admitted turn");
        return completion.complete(new SavedResult(saved.getText(), saved.getStones(), nanoClock.getAsLong()));
    }
    public boolean fail(RuntimeException failure) { return completion.completeExceptionally(failure); }
    public long confirmedAtNanos() {
        if (!completion.isDone() || completion.isCompletedExceptionally()) return Long.MIN_VALUE;
        return completion.getNow(null).confirmedAtNanos();
    }
    public void transportExited() { if (transportExited.compareAndSet(false, true)) releaseTransport.run(); }
    public synchronized StoneChatStreamEventEntity nextEvent(int chunkCodePoints) {
        if (chunkCodePoints <= 0) throw new IllegalArgumentException("Positive literal chunk size required");
        if (terminal || transportExited.get()) return null;
        if (!started) { started = true; return event(StoneChatStreamEventType.STARTED, null, List.of(), null); }
        if (!completion.isDone()) return null;
        SavedResult saved;
        try { saved = completion.getNow(null); }
        catch (CompletionException exception) {
            terminal = true;
            return event(StoneChatStreamEventType.ERROR, null, List.of(), exception.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(exception.getCause()));
        }
        if (admittedTurn.getReplayTurn() == null && textOffset < saved.text().length()) {
            int end = textOffset;
            for (int index = 0; index < chunkCodePoints && end < saved.text().length(); index++) end += Character.charCount(saved.text().codePointAt(end));
            String text = saved.text().substring(textOffset, end); textOffset = end;
            return event(StoneChatStreamEventType.TEXT_DELTA, text, List.of(), null);
        }
        terminal = true; return event(StoneChatStreamEventType.COMPLETED, saved.text(), saved.stones(), null);
    }
    private StoneChatStreamEventEntity event(StoneChatStreamEventType type, String text, List<StoneChatStoneEntity> stones, RuntimeException failure) {
        StoneChatStreamEventEntity event = new StoneChatStreamEventEntity(); event.setType(type); event.setConversationId(admittedTurn.getConversationId());
        event.setTurnId(admittedTurn.getRequestTurn().getTurnId()); event.setSequence(++sequence); event.setText(text); event.setStones(stones); event.setFailure(failure); return event;
    }
}
