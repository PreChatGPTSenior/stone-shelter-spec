package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Optional ordering for a stone search.")
public record SearchSort(
    @Schema(description = "Sort field: name, stoneSize or admissionDate; uses admissionDate when omitted.")
    @Pattern(regexp = "name|stoneSize|admissionDate") String field,
    @Schema(description = "Sort direction: asc or desc; defaults to desc for admissionDate and asc for name or stoneSize.")
    @Pattern(regexp = "asc|desc") String direction
) {}
