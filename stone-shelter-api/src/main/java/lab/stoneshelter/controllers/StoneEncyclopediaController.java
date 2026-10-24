package lab.stoneshelter.controllers;

import java.io.IOException;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;
import lab.stoneshelter.exceptions.InvalidStoneDocumentIndexRequestException;
import lab.stoneshelter.services.StoneEncyclopediaIndexService;
import lab.stoneshelter.shared.StoneEncyclopediaIndexResponse;
import lab.stoneshelter.shared.StoneEncyclopediaDeleteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ControllerAdvice(assignableTypes = StoneEncyclopediaController.class)
@RequestMapping("/api/stone-encyclopedia/index")
public class StoneEncyclopediaController implements ResponseBodyAdvice<StoneEncyclopediaIndexResponse> {
    private final StoneEncyclopediaIndexService service;
    public StoneEncyclopediaController(StoneEncyclopediaIndexService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import the configured Markdown source synchronously", description = "Empty body. A new UUID v4 Idempotency-Key starts an import; reuse it after a lost response. Successful results replay for three days. Clients and proxies must allow the configured two-hour import deadline plus a response margin.")
    @ApiResponse(responseCode = "201", description = "First projection created, or replay of that result.", content = @Content(schema = @Schema(implementation = StoneEncyclopediaIndexResponse.class)))
    @ApiResponse(responseCode = "200", description = "Projection replaced, or replay of that result.", content = @Content(schema = @Schema(implementation = StoneEncyclopediaIndexResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid key or nonempty body.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Configured MinIO source missing.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Active operation or conflicting, expired or failed key.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Source cannot be indexed.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Dependency unavailable or outcome uncertain.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public StoneEncyclopediaIndexResponse importIndex(
            @Parameter(description = "UUID v4 operation identity; reuse the same key only for a retry.", required = true,
                    schema = @Schema(type = "string", format = "uuid", pattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        return service.importIndex(validate(key, request));
    }

    @DeleteMapping
    @Operation(summary = "Remove the configured document projection", description = "Empty body; UUID v4 Idempotency-Key required. The MinIO source remains intact. Successful deletion replays for three days and never deletes a later projection.")
    @ApiResponse(responseCode = "200", description = "Index removed or already absent; historical deletion replay.", content = @Content(schema = @Schema(implementation = StoneEncyclopediaDeleteResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid key or nonempty body.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Active operation or conflicting, expired or failed key.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Dependency unavailable or outcome uncertain.", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public StoneEncyclopediaDeleteResponse delete(
            @Parameter(description = "UUID v4 deletion identity.", required = true, schema = @Schema(type = "string", format = "uuid",
                    pattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
            @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        return service.delete(validate(key, request));
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getParameterType() == StoneEncyclopediaIndexResponse.class;
    }

    @Override
    public StoneEncyclopediaIndexResponse beforeBodyWrite(StoneEncyclopediaIndexResponse body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body.outcome() == StoneDocumentIndexOutcome.REPLACED) response.setStatusCode(HttpStatus.OK);
        return body;
    }

    private UUID validate(String key, HttpServletRequest request) {
        if (!key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")) {
            throw new InvalidStoneDocumentIndexRequestException("Idempotency-Key must be a UUID v4.");
        }
        try {
            if (request.getInputStream().read() != -1) throw new InvalidStoneDocumentIndexRequestException("The request body must be empty.");
        } catch (IOException exception) { throw new InvalidStoneDocumentIndexRequestException("The request body could not be read."); }
        return UUID.fromString(key);
    }
}
