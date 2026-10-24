package lab.stoneshelter.criteria;

import java.util.List;
import java.util.Objects;

public record StoneSemanticSearchCriteria(StoneExactSearchCriteria exact, List<StoneSemanticWish> wishes) {
    public StoneSemanticSearchCriteria {
        Objects.requireNonNull(exact, "Effective exact criteria are required");
        wishes = List.copyOf(wishes);
        if (wishes.stream().map(StoneSemanticWish::key).distinct().count() != wishes.size()) {
            throw new IllegalArgumentException("Wish keys must be distinct");
        }
    }
}
