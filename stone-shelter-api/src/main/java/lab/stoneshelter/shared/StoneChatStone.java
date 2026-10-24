package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A stone reference; the UI constructs its canonical details URL.")
public record StoneChatStone(
        @Schema(description = "Positive stone identifier.", requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(description = "Stone name used as the link label.", requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
