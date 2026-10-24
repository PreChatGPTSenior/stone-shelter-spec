package lab.stoneshelter.entities;

import java.util.UUID;
import java.util.List;
import lab.stoneshelter.enums.StoneChatStreamEventType;

public class StoneChatStreamEventEntity {
    private StoneChatStreamEventType type;
    private UUID conversationId;
    private UUID turnId;
    private long sequence;
    private String text;
    private List<StoneChatStoneEntity> stones;
    private RuntimeException failure;
    public StoneChatStreamEventEntity() {}
    public StoneChatStreamEventType getType() { return type; }
    public void setType(StoneChatStreamEventType value) { type = value; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID value) { conversationId = value; }
    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID value) { turnId = value; }
    public long getSequence() { return sequence; }
    public void setSequence(long value) { sequence = value; }
    public String getText() { return text; }
    public void setText(String value) { text = value; }
    public List<StoneChatStoneEntity> getStones() { return stones; }
    public void setStones(List<StoneChatStoneEntity> value) { stones = value; }
    public RuntimeException getFailure() { return failure; }
    public void setFailure(RuntimeException value) { failure = value; }
}
