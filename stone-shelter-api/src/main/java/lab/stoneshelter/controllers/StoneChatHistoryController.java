package lab.stoneshelter.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import java.util.UUID;
import lab.stoneshelter.services.StoneChatbotService;
import lab.stoneshelter.services.StoneChatAdmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lab.stoneshelter.shared.StoneChatHistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/conversations")
public class StoneChatHistoryController {
    private final StoneChatbotService chatbot;
    private final StoneChatAdmissionService admission;
    public StoneChatHistoryController(StoneChatbotService chatbot, StoneChatAdmissionService admission) { this.chatbot = chatbot; this.admission = admission; }
    @SecurityRequirement(name = "stoneChatSession")
    @Operation(summary = "Restore committed conversation history", description = "Cookie ownership is required for retained UUIDs. Healthy missing/expired history returns empty messages; foreign ownership returns 404. Does not refresh retention. Manual GET verifies required chat components during recovery; no automatic POST replay.")
    @ApiResponse(responseCode = "200", description = "Chronological complete pairs, including turnId, context and ordered references.", content = @Content(schema = @Schema(implementation = StoneChatHistoryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid UUID, required turnId, message or context.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "CHAT_CONVERSATION_NOT_FOUND: retained UUID is foreign, including lost cookie.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", headers = @Header(name = "Retry-After", description = "Positive whole seconds until manual retry, rounded up; conversation-busy hint is 1.", schema = @Schema(type = "integer", minimum = "1")), description = "CHAT_RATE_LIMITED; Retry-After is positive refill seconds.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "CHAT_UNAVAILABLE: required dependency unavailable or outcome unknown; manual history recovery required.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(value = "/{conversationId}/messages", produces = "application/json")
    public StoneChatHistoryResponse findById(@PathVariable UUID conversationId, @RequestAttribute(StoneChatBindingInterceptor.SESSION) String sessionId, HttpServletRequest request, HttpServletResponse response) {
        StoneChatHistoryResponse history = chatbot.findById(conversationId, sessionId);
        StoneChatBindingInterceptor.writeCookie(response, request, sessionId, admission.remainingRetentionSeconds(sessionId));
        return history;
    }
}
