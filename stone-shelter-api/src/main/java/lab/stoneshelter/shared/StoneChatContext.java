package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Current page context; no catalog lookup is performed by the stub.")
public record StoneChatContext(
        @Min(1) @Max(9007199254740991L)
        @Schema(description = "Current stone details identifier; null outside a valid details route.",
                nullable = true) Long stoneId) {}
