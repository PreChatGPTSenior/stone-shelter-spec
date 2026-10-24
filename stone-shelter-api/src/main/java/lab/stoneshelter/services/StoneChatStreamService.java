package lab.stoneshelter.services;

import lab.stoneshelter.entities.StoneChatStreamEntity;
import lab.stoneshelter.mappers.dtos.StoneChatStreamEntityToStoneChatStreamResponseMapper;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import lab.stoneshelter.shared.StoneChatStreamResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneChatStreamService {
    private final StoneChatbotService chatbot;
    private final StoneChatStreamAdmissionService admission;
    private final StoneChatStreamEntityToStoneChatStreamResponseMapper mapper;
    public StoneChatStreamService(StoneChatbotService chatbot, StoneChatStreamAdmissionService admission, StoneChatStreamEntityToStoneChatStreamResponseMapper mapper) {
        this.chatbot = chatbot; this.admission = admission; this.mapper = mapper;
    }
    public StoneChatStreamResponse sendMessage(StoneChatMessageRequest request, String sessionId) {
        StoneChatStreamEntity stream = chatbot.startMessage(request, sessionId, () -> admission.acquire()::close);
        try { return mapper.toDto(stream); }
        catch (RuntimeException exception) { stream.transportExited(); throw exception; }
    }
}
