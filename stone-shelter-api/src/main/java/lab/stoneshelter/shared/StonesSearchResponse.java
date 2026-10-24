package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A page of stone search results.")
public record StonesSearchResponse(
    @Schema(description = "Stone entries on the requested page.")
    List<StoneSearchResponse> content,
    @Schema(description = "Zero-based index of the returned page.")
    int page,
    @Schema(description = "Requested page size.")
    int size,
    @Schema(description = "Total number of stones matching the filters.")
    long totalElements
) {}
