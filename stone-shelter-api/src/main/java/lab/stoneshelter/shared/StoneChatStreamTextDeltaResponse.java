package lab.stoneshelter.shared;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Text-delta SSE data: nonempty literal fragment of the confirmed saved answer.")
public record StoneChatStreamTextDeltaResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID turnId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long sequence, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1) String text) {}
