package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Assistant text and ordered stone references for UI details links.")
public record StoneChatMessageResponse(
        @Schema(description = "Plain-text assistant message.", requiredMode = Schema.RequiredMode.REQUIRED)
        String text,
        @Schema(description = "Ordered stone references; an empty array is valid.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<StoneChatStone> stones) {}
