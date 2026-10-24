package lab.stoneshelter.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lab.stoneshelter.services.StonePhotoService;
import lab.stoneshelter.shared.StonePhotoUploadRequest;
import lab.stoneshelter.shared.StonePhotoUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stones/{id}/photos")
public class StonePhotoController {
    private final StonePhotoService service;
    public StonePhotoController(StonePhotoService service) { this.service = service; }

    @Operation(summary = "Append one image to the stone gallery", description = "JPEG, PNG or WebP, up to 10 MiB. First successful addition remains the cover. Read URLs last one hour.")
    @ApiResponse(responseCode = "201", description = "Photo stored and appended.", content = @Content(schema = @Schema(implementation = StonePhotoUploadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing, empty or corrupt image.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Stone does not exist.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "File exceeds 10 MiB.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Unsupported or mismatched media type.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Photo storage unavailable.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StonePhotoUploadResponse uploadPhoto(@PathVariable("id") long id,
            @Valid @ModelAttribute StonePhotoUploadRequest request) {
        return service.upload(id, request);
    }
}
