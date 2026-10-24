package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Applicant details for reserving an available stone.")
public record StoneReservationCreateRequest(
        @NotBlank @Schema(description = "Applicant name as nonblank arbitrary text.", requiredMode = Schema.RequiredMode.REQUIRED)
        String applicantName,
        @NotBlank @Schema(description = "Nonblank contact details; no phone or email format is required.", requiredMode = Schema.RequiredMode.REQUIRED)
        String contactDetails) {}
