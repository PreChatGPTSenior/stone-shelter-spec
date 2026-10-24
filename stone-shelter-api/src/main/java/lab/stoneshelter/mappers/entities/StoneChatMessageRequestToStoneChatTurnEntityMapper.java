package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneChatContextEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import org.springframework.stereotype.Component;

@Component
public class StoneChatMessageRequestToStoneChatTurnEntityMapper extends AbstractEntityMapper<StoneChatMessageRequest, StoneChatTurnEntity> {
    @Override protected StoneChatTurnEntity newEntity() { return new StoneChatTurnEntity(); }
    @Override protected StoneChatTurnEntity populateEntity(StoneChatMessageRequest request, StoneChatTurnEntity turn) { turn.setTurnId(request.turnId()); turn.setMessage(request.message());
        turn.setContext(request.context() == null ? null : new StoneChatContextEntity(request.context().stoneId()));
        return turn;
    }
}
