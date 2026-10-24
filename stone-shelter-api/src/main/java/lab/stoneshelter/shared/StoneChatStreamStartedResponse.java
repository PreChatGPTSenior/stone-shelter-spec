package lab.stoneshelter.shared;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Started SSE data: admitted only; no persistence claim.")
public record StoneChatStreamStartedResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID turnId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long sequence) {}
