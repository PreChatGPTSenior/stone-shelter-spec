package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.shared.StoneChatStreamErrorContext;
import lab.stoneshelter.shared.StoneChatStreamErrorResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper extends AbstractDtoMapper<StoneChatStreamErrorContext, StoneChatStreamErrorResponse> {
    @Override protected StoneChatStreamErrorResponse mapToDto(StoneChatStreamErrorContext source) {
        return new StoneChatStreamErrorResponse(source.conversationId(), source.turnId(), source.sequence(), source.problem());
    }
}
