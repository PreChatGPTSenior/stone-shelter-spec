package lab.stoneshelter.shared;

import java.util.List;

public record StoneDocumentSearchResponse(List<StoneDocumentPassageResponse> passages) {
    public StoneDocumentSearchResponse { passages = List.copyOf(passages); }
}
