package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identifier of the deleted stone catalog entry.")
public record StoneDeleteResponse(
    @Schema(description = "Unique stone identifier.") Long id
) {}
