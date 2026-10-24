package lab.stoneshelter.entities;

import java.util.List;

public record StoneCardSearchResultEntity(List<Match> matches) {
    public StoneCardSearchResultEntity { matches = List.copyOf(matches); }
    public record Match(Long stoneId, double score) {}
}
