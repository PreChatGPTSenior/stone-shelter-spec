package lab.stoneshelter.entities;

import java.util.List;
import java.util.UUID;

public record StoneChatConversationEntity(int version, UUID conversationId, List<StoneChatTurnEntity> turns) {}
