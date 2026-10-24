package lab.stoneshelter.shared;

import java.util.UUID;
import java.time.Instant;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreType
@Schema(hidden = true)
public class StoneChatStreamResponse {
    private UUID conversationId;
    private UUID turnId;
    private boolean replay;
    private boolean renewCookie;
    private Instant processingDeadline;
    private LongSupplier confirmedAtNanos;
    private Supplier<StoneChatStreamEventResponse> nextEvent;
    private Runnable onTransportExit;
    public StoneChatStreamResponse() {}
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID value) { conversationId = value; }
    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID value) { turnId = value; }
    public boolean getReplay() { return replay; }
    public void setReplay(boolean value) { replay = value; }
    public boolean getRenewCookie() { return renewCookie; }
    public void setRenewCookie(boolean value) { renewCookie = value; }
    public Instant getProcessingDeadline() { return processingDeadline; }
    public void setProcessingDeadline(Instant value) { processingDeadline = value; }
    public LongSupplier getConfirmedAtNanos() { return confirmedAtNanos; }
    public void setConfirmedAtNanos(LongSupplier value) { confirmedAtNanos = value; }
    public Supplier<StoneChatStreamEventResponse> getNextEvent() { return nextEvent; }
    public void setNextEvent(Supplier<StoneChatStreamEventResponse> value) { nextEvent = value; }
    public Runnable getOnTransportExit() { return onTransportExit; }
    public void setOnTransportExit(Runnable value) { onTransportExit = value; }
}
