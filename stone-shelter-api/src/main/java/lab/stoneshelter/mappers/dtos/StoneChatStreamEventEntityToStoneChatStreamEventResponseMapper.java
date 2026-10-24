package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneChatStreamEventEntity;
import lab.stoneshelter.shared.StoneChatStreamEventResponse;
import lab.stoneshelter.shared.StoneChatStreamStartedResponse;
import lab.stoneshelter.shared.StoneChatStreamTextDeltaResponse;
import lab.stoneshelter.shared.StoneChatStreamCompletedResponse;
import lab.stoneshelter.shared.StoneChatStone;
import org.springframework.stereotype.Component;

@Component
public class StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper extends AbstractDtoMapper<StoneChatStreamEventEntity, StoneChatStreamEventResponse> {
    @Override protected StoneChatStreamEventResponse mapToDto(StoneChatStreamEventEntity source) {
        return new StoneChatStreamEventResponse(source.getType().getWireName(), switch (source.getType()) {
            case STARTED -> new StoneChatStreamStartedResponse(source.getConversationId(), source.getTurnId(), source.getSequence());
            case TEXT_DELTA -> new StoneChatStreamTextDeltaResponse(source.getConversationId(), source.getTurnId(), source.getSequence(), source.getText());
            case COMPLETED -> completed(source);
            case ERROR -> null;
        }, source.getFailure(), source.getSequence());
    }
    private StoneChatStreamCompletedResponse completed(StoneChatStreamEventEntity source) {
        StoneChatStreamCompletedResponse target = new StoneChatStreamCompletedResponse(); target.setConversationId(source.getConversationId()); target.setTurnId(source.getTurnId());
        target.setSequence(source.getSequence()); target.setText(source.getText()); target.setStones(source.getStones().stream().map(stone -> new StoneChatStone(stone.id(), stone.name())).toList()); return target;
    }
}
