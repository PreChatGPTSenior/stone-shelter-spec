package lab.stoneshelter.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;

@Schema(description = "Committed document index import result.")
public record StoneEncyclopediaIndexResponse(
        @Schema(description = "Stable configured document identity.") String documentKey,
        @Schema(description = "SHA-256 of the imported source bytes.") String sourceVersion,
        @Schema(description = "Number of published passages.") int indexedFragments,
        @JsonIgnore @Schema(hidden = true) StoneDocumentIndexOutcome outcome) { }
