package lab.stoneshelter.shared;

import java.util.UUID;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Completed SSE data: full confirmed saved answer and ordered references; terminal success.")
public class StoneChatStreamCompletedResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID conversationId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID turnId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private long sequence;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StoneChatStone> stones;
    public StoneChatStreamCompletedResponse() {}
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID value) { conversationId = value; }
    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID value) { turnId = value; }
    public long getSequence() { return sequence; }
    public void setSequence(long value) { sequence = value; }
    public String getText() { return text; }
    public void setText(String value) { text = value; }
    public List<StoneChatStone> getStones() { return stones; }
    public void setStones(List<StoneChatStone> value) { stones = value; }
}
