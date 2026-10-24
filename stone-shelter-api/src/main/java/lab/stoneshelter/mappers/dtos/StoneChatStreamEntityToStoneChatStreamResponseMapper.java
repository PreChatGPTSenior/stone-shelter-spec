package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneChatStreamEntity;
import lab.stoneshelter.services.StoneChatStreamSettings;
import lab.stoneshelter.shared.StoneChatStreamResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneChatStreamEntityToStoneChatStreamResponseMapper extends AbstractDtoMapper<StoneChatStreamEntity, StoneChatStreamResponse> {
    private final StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper eventMapper;
    private final StoneChatStreamSettings settings;
    public StoneChatStreamEntityToStoneChatStreamResponseMapper(StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper eventMapper, StoneChatStreamSettings settings) {
        this.eventMapper = eventMapper; this.settings = settings;
    }
    @Override protected StoneChatStreamResponse mapToDto(StoneChatStreamEntity source) {
        StoneChatStreamResponse target = new StoneChatStreamResponse(); target.setConversationId(source.getAdmittedTurn().getConversationId());
        target.setTurnId(source.getAdmittedTurn().getRequestTurn().getTurnId()); target.setReplay(source.getAdmittedTurn().getReplayTurn() != null);
        target.setRenewCookie(source.getAdmittedTurn().getReplayTurn() == null); target.setProcessingDeadline(source.getAdmittedTurn().getDeadline());
        target.setConfirmedAtNanos(source::confirmedAtNanos); target.setOnTransportExit(source::transportExited);
        target.setNextEvent(() -> { var stoneChatStreamEventEntity = source.nextEvent(settings.chunkCodePoints()); return stoneChatStreamEventEntity == null ? null : eventMapper.toDto(stoneChatStreamEventEntity); });
        return target;
    }
}
