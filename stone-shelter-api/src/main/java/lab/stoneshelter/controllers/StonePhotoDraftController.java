package lab.stoneshelter.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lab.stoneshelter.services.StonePhotoDraftService;
import lab.stoneshelter.shared.StonePhotoDraftUploadRequest;
import lab.stoneshelter.shared.StonePhotoDraftUploadResponse;
import lab.stoneshelter.shared.StonePhotoDraftResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stone-photo-drafts")
public class StonePhotoDraftController {
    private final StonePhotoDraftService service;
    public StonePhotoDraftController(StonePhotoDraftService service) { this.service = service; }

    @Operation(summary = "Upload a photograph before creating a stone")
    @ApiResponse(responseCode = "201", description = "Draft stored for 24 hours.", content = @Content(schema = @Schema(implementation = StonePhotoDraftUploadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing, empty or corrupt image.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "File exceeds 10 MiB.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Unsupported or mismatched media type.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Draft storage unavailable.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Draft metadata could not be persisted.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StonePhotoDraftUploadResponse upload(@Valid @ModelAttribute StonePhotoDraftUploadRequest request) {
        return service.upload(request);
    }

    @Operation(summary = "Refresh an unassociated draft preview without renewing its lifetime")
    @ApiResponse(responseCode = "200", description = "Unexpired draft.", content = @Content(schema = @Schema(implementation = StonePhotoDraftResponse.class)))
    @ApiResponse(responseCode = "400", description = "Malformed UUID.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Unknown, expired or associated draft.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Draft storage unavailable.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public StonePhotoDraftResponse findById(@PathVariable("id") UUID id) { return service.findById(id); }
}
