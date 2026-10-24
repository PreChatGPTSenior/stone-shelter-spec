package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Unassociated draft photograph with a refreshed preview URL.")
public record StonePhotoDraftResponse(
        @Schema(description = "Unique draft upload reference.", format = "uuid") UUID id,
        @Schema(description = "Private preview URL, valid at most one hour and until upload expiry.") String url,
        @Schema(description = "Original successful upload timestamp.") Instant uploadedAt,
        @Schema(description = "Original expiration timestamp; never renewed by reading.") Instant expiresAt) {}
