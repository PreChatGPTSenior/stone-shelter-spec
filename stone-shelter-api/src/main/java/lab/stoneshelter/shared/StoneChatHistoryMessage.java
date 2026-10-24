package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.enums.ChatMessageRole;

public class StoneChatHistoryMessage {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ChatMessageRole role;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StoneChatStone> stones;
    @Schema(nullable = true)
    private StoneChatContext context;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID turnId;
    public StoneChatHistoryMessage() {}
    public ChatMessageRole getRole() { return role; }
    public void setRole(ChatMessageRole role) { this.role = role; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<StoneChatStone> getStones() { return stones; }
    public void setStones(List<StoneChatStone> stones) { this.stones = stones; }
    public StoneChatContext getContext() { return context; }
    public void setContext(StoneChatContext context) { this.context = context; }
    public UUID getTurnId() { return turnId; }
    public void setTurnId(UUID turnId) { this.turnId = turnId; }
}
