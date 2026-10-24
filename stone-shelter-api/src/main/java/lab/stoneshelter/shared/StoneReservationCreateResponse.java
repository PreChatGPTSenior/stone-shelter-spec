package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lab.stoneshelter.enums.AdoptionStatus;

@Schema(description = "Confirmation of a created reservation and the stone's reserved status.")
public record StoneReservationCreateResponse(
        @Schema(description = "Generated reservation identifier.") Long id,
        @Schema(description = "Identifier of the reserved stone.") Long stoneId,
        @Schema(description = "Stone adoption status after reservation creation.", allowableValues = "RESERVED") AdoptionStatus adoptionStatus,
        @Schema(description = "Server-assigned creation timestamp in UTC.") Instant createdAt) {}
