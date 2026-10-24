package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A managed gallery photo in stable successful-addition order.")
public record StonePhotoResponse(
        @Schema(description = "Stable photo identifier.") Long id,
        @Schema(description = "Browser-accessible private-object read URL, valid for one hour.") String url,
        @Schema(description = "Server-assigned timestamp of successful addition.") Instant addedAt,
        @Schema(description = "Zero-based per-stone successful-addition position.", minimum = "0") int position) {}
