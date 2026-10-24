package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Schema(description = "Optional filters, pagination, and ordering for an AVAILABLE-only catalog search.")
public record StonesSearchRequest(
    @Schema(description = "Optional exact-match filters; omitted filters retain mandatory AVAILABLE visibility.")
    @Valid StoneSearchFilter filter,
    @Schema(description = "Zero-based page index; defaults to 0 when omitted.")
    @Min(0) Integer page,
    @Schema(description = "Page size from 1 to 24; defaults to 12 when omitted.")
    @Min(1) @Max(24) Integer size,
    @Schema(description = "Optional ordering; defaults to admissionDate descending, with id ascending to break ties.")
    @Valid SearchSort sort
) {}
