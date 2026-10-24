package lab.stoneshelter.controllers;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import lab.stoneshelter.services.StoneService;
import lab.stoneshelter.shared.StoneCreateRequest;
import lab.stoneshelter.shared.StoneCreateResponse;
import lab.stoneshelter.shared.StoneDeleteResponse;
import lab.stoneshelter.shared.StoneResponse;
import lab.stoneshelter.shared.StoneUpdateRequest;
import lab.stoneshelter.shared.StoneUpdateResponse;
import lab.stoneshelter.shared.StonesSearchRequest;
import lab.stoneshelter.shared.StonesSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/stones")
public class StoneCatalogController {
    private final StoneService service;

    public StoneCatalogController(StoneService service) {
        this.service = service;
    }

    @Operation(summary = "Create a stone with up to 16 previously uploaded draft photographs",
            description = "Creation succeeds after database association. Internal permanent-copy failures use placeholders and do not fail creation.")
    @ApiResponse(responseCode = "201", description = "Stone and gallery associations committed.", content = @Content(schema = @Schema(implementation = StoneCreateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid stone details or unknown, expired, duplicate or excessive photo references.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A draft photograph was already consumed.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "Stone could not be persisted; drafts remain reusable until expiry.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoneCreateResponse createStone(
            @Valid @RequestBody StoneCreateRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public StoneResponse getStone(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @Operation(summary = "Search AVAILABLE stones in the catalog",
            description = "Only AVAILABLE stones are returned and counted before pagination. Filters combine with this restriction using AND; RESERVED or ADOPTED status filters return an empty page with totalElements 0.")
    @PostMapping("/search")
    public StonesSearchResponse searchStones(
            @Valid @RequestBody StonesSearchRequest request) {
        return service.search(request);
    }

    @PutMapping("/{id}")
    public StoneUpdateResponse updateStone(
            @PathVariable("id") Long id,
            @Valid @RequestBody StoneUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public StoneDeleteResponse deleteStone(@PathVariable("id") Long id) {
        return service.delete(id);
    }
}
