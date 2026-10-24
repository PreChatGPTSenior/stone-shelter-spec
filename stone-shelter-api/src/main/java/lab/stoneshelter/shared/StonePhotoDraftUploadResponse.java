package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Successfully stored draft photograph, reusable for 24 hours.")
public record StonePhotoDraftUploadResponse(
        @Schema(description = "Unique draft upload reference.", format = "uuid") UUID id,
        @Schema(description = "Private preview URL, valid at most one hour and until upload expiry.") String url,
        @Schema(description = "Successful draft upload timestamp.") Instant uploadedAt,
        @Schema(description = "Unassociated upload expiration timestamp.") Instant expiresAt) {}
