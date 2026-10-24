package lab.stoneshelter.mappers.dtos;

import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.entities.StoneChatConversationEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.enums.ChatMessageRole;
import lab.stoneshelter.shared.StoneChatContext;
import lab.stoneshelter.shared.StoneChatHistoryMessage;
import lab.stoneshelter.shared.StoneChatHistoryResponse;
import lab.stoneshelter.shared.StoneChatStone;
import org.springframework.stereotype.Component;

@Component
public class StoneChatConversationEntityToStoneChatHistoryResponseMapper extends AbstractDtoMapper<StoneChatConversationEntity, StoneChatHistoryResponse> {
    @Override protected StoneChatHistoryResponse mapToDto(StoneChatConversationEntity conversation) {
        List<StoneChatHistoryMessage> messages = new ArrayList<>();
        for (StoneChatTurnEntity turn : conversation.turns()) {
            StoneChatHistoryMessage user = new StoneChatHistoryMessage(); user.setRole(ChatMessageRole.USER);
            user.setText(turn.getMessage()); user.setStones(List.of()); user.setTurnId(turn.getTurnId());
            user.setContext(turn.getContext() == null ? null : new StoneChatContext(turn.getContext().stoneId())); messages.add(user);
            StoneChatHistoryMessage assistant = new StoneChatHistoryMessage(); assistant.setRole(ChatMessageRole.ASSISTANT);
            assistant.setText(turn.getText()); assistant.setContext(null); assistant.setTurnId(turn.getTurnId());
            assistant.setStones(turn.getStones().stream().map(stone -> new StoneChatStone(stone.id(), stone.name())).toList()); messages.add(assistant);
        }
        return new StoneChatHistoryResponse(conversation.conversationId(), List.copyOf(messages));
    }
}
