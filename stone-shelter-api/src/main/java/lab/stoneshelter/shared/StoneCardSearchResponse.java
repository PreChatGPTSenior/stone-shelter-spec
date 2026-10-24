package lab.stoneshelter.shared;

import java.util.List;

public record StoneCardSearchResponse(List<StoneCardMatchResponse> matches) {
    public StoneCardSearchResponse { matches = List.copyOf(matches); }
}
