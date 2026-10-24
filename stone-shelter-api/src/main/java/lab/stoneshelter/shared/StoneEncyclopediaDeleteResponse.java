package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identity of the removed document projection.")
public record StoneEncyclopediaDeleteResponse(@Schema(description = "Stable document identity.") String id) { }
