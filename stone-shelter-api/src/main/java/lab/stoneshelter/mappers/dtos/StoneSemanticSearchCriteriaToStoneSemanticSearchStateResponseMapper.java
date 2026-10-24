package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.shared.StoneSemanticSearchStateResponse;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticSearchCriteriaToStoneSemanticSearchStateResponseMapper extends AbstractDtoMapper<StoneSemanticSearchCriteria, StoneSemanticSearchStateResponse> {
    @Override protected StoneSemanticSearchStateResponse mapToDto(StoneSemanticSearchCriteria source) {
        return new StoneSemanticSearchStateResponse(source.exact(), source.wishes().stream()
                .map(wish -> new StoneSemanticWishRequest(wish.key(), wish.text())).toList(), null, false);
    }
}
