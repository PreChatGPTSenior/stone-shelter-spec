package lab.stoneshelter.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lab.stoneshelter.services.StoneChatAdmissionService;
import lab.stoneshelter.services.StoneChatStreamService;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import lab.stoneshelter.shared.StoneChatStreamResponse;
import lab.stoneshelter.shared.StoneChatStreamStartedResponse;
import lab.stoneshelter.shared.StoneChatStreamTextDeltaResponse;
import lab.stoneshelter.shared.StoneChatStreamCompletedResponse;
import lab.stoneshelter.shared.StoneChatStreamErrorResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1/chat/messages")
public class StoneChatStreamController {
    private final StoneChatStreamService stream;
    private final StoneChatAdmissionService admission;
    public StoneChatStreamController(StoneChatStreamService stream, StoneChatAdmissionService admission) { this.stream = stream; this.admission = admission; }
    @SecurityRequirement(name = "stoneChatSession")
    @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true, description = "Exact configured frontend origin.")
    @Operation(summary = "Stream a confirmed stone-chat answer", description = "Named SSE events: started, text-delta, completed, error; JSON data has conversationId, turnId and contiguous sequence starting at 1. Only an acknowledged atomic complete pair supplies answer fragments. References occur only in completed. Replay is started/completed without deltas or retention refresh. Heartbeat comments have no sequence, id or retry; disconnect stops delivery only. Pre-open errors use HTTP ProblemDetail/Retry-After; post-open error.problem retains business status/code and optional positive retryAfterSeconds. Unknown commit requires authoritative history recovery. UTF-8, no-store; bounded separate stream capacity/deadlines.")
    @ApiResponse(responseCode = "200", description = "Admitted transport; HTTP 200 alone does not confirm a saved pair. Exactly one terminal event when deliverable.", content = @Content(mediaType = "text/event-stream", schema = @Schema(oneOf = { StoneChatStreamStartedResponse.class, StoneChatStreamTextDeltaResponse.class, StoneChatStreamCompletedResponse.class, StoneChatStreamErrorResponse.class })))
    @ApiResponse(responseCode = "400", description = "Invalid existing request constraints.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CHAT_ORIGIN_REJECTED.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "CHAT_CONVERSATION_NOT_FOUND; foreign or lost owner.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "CHAT_TURN_CONFLICT; changed retained payload.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Shared quota, busy or token budget rejection.", headers = @Header(name = "Retry-After", schema = @Schema(type = "integer", minimum = "1")), content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "CHAT_INVALID_MODEL_OUTPUT.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "CHAT_STREAM_CAPACITY_EXCEEDED or CHAT_CAPACITY_EXCEEDED: Retry-After 1; local limits/dependency failure retain existing distinctions.", headers = @Header(name = "Retry-After", schema = @Schema(type = "integer", minimum = "1")), content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(value = "/stream", consumes = "application/json", produces = "text/event-stream")
    public StoneChatStreamResponse sendMessage(@Valid @RequestBody StoneChatMessageRequest request,
            @RequestAttribute(StoneChatBindingInterceptor.SESSION) String sessionId, HttpServletRequest httpRequest, HttpServletResponse response) {
        StoneChatStreamResponse result = stream.sendMessage(request, sessionId);
        if (result.getRenewCookie()) StoneChatBindingInterceptor.writeCookie(response, httpRequest, sessionId, admission.retentionSeconds());
        return result;
    }
}
