package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneCardSearchResultEntity;
import lab.stoneshelter.shared.StoneCardMatchResponse;
import lab.stoneshelter.shared.StoneCardSearchResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneCardSearchResultEntityToStoneCardSearchResponseMapper
        extends AbstractDtoMapper<StoneCardSearchResultEntity, StoneCardSearchResponse> {
    @Override
    protected StoneCardSearchResponse mapToDto(StoneCardSearchResultEntity stoneCardSearchResultEntity) {
        return new StoneCardSearchResponse(stoneCardSearchResultEntity.matches().stream()
                .map(match -> new StoneCardMatchResponse(match.stoneId(), match.score())).toList());
    }
}
