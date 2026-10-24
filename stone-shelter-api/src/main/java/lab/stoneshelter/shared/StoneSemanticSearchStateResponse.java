package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.enums.StoneSemanticSearchMode;

public record StoneSemanticSearchStateResponse(StoneExactSearchCriteria exact,
        List<StoneSemanticWishRequest> wishes, StoneSemanticSearchMode mode, boolean actualSelection) {}
