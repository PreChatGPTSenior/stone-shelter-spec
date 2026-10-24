package lab.stoneshelter.controllers;

import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lab.stoneshelter.services.StoneCardReindexService;
import lab.stoneshelter.shared.StoneCardReindexStartRequest;
import lab.stoneshelter.shared.StoneCardReindexStartResponse;
import lab.stoneshelter.shared.StoneCardReindexStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stone-card-reindex-jobs")
public class StoneCardReindexController {
    private final StoneCardReindexService stoneCardReindexService;

    public StoneCardReindexController(StoneCardReindexService stoneCardReindexService) {
        this.stoneCardReindexService = stoneCardReindexService;
    }

    @Operation(summary = "Start explicit card reindexing", description = "Creates one durable ALL or SELECTED job. A full job blocks new card creation until its events are enqueued.")
    @ApiResponse(responseCode = "201", description = "Durable asynchronous job created.", content = @Content(schema = @Schema(implementation = StoneCardReindexStartResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid selection.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "A selected card does not exist.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Another job is active.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Required storage is unavailable.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoneCardReindexStartResponse start(@Valid @RequestBody StoneCardReindexStartRequest stoneCardReindexStartRequest) {
        return stoneCardReindexService.start(stoneCardReindexStartRequest);
    }

    @Operation(summary = "Get durable reindex preparation progress", description = "ENQUEUED means outbox preparation has ended, including recorded item errors. It does not mean Kafka delivery or final vector readiness.")
    @ApiResponse(responseCode = "200", description = "Durable job progress.", content = @Content(schema = @Schema(implementation = StoneCardReindexStatusResponse.class)))
    @ApiResponse(responseCode = "404", description = "Unknown job.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Required storage is unavailable.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{jobId}")
    public StoneCardReindexStatusResponse findById(@PathVariable UUID jobId) {
        return stoneCardReindexService.findById(jobId);
    }
}
