package lab.stoneshelter.shared;

import java.util.UUID;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error SSE data: terminal public problem; status is the business failure after HTTP opening.")
public record StoneChatStreamErrorResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID turnId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long sequence, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProblemDetail problem) {}
