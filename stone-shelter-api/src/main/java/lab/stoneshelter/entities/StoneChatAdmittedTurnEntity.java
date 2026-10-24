package lab.stoneshelter.entities;

import java.util.UUID;
import java.time.Instant;

public class StoneChatAdmittedTurnEntity {
    private UUID conversationId;
    private String sessionId;
    private StoneChatTurnEntity requestTurn;
    private StoneChatTurnEntity replayTurn;
    private Instant deadline;
    public StoneChatAdmittedTurnEntity() {}
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID value) { conversationId = value; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { sessionId = value; }
    public StoneChatTurnEntity getRequestTurn() { return requestTurn; }
    public void setRequestTurn(StoneChatTurnEntity value) { requestTurn = value; }
    public StoneChatTurnEntity getReplayTurn() { return replayTurn; }
    public void setReplayTurn(StoneChatTurnEntity value) { replayTurn = value; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant value) { deadline = value; }
}
