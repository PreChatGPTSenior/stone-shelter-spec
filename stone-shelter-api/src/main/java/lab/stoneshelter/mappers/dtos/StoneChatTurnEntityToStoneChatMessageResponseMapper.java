package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.shared.StoneChatMessageResponse;
import lab.stoneshelter.shared.StoneChatStone;
import org.springframework.stereotype.Component;

@Component
public class StoneChatTurnEntityToStoneChatMessageResponseMapper extends AbstractDtoMapper<StoneChatTurnEntity, StoneChatMessageResponse> {
    @Override protected StoneChatMessageResponse mapToDto(StoneChatTurnEntity turn) {
        return new StoneChatMessageResponse(turn.getText(), turn.getStones().stream()
                .map(stone -> new StoneChatStone(stone.id(), stone.name())).toList());
    }
}
