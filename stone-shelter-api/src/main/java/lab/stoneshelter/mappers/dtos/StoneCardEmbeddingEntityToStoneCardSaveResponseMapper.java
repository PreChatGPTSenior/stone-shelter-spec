package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.shared.StoneCardSaveResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneCardEmbeddingEntityToStoneCardSaveResponseMapper
        extends AbstractDtoMapper<StoneCardEmbeddingEntity, StoneCardSaveResponse> {
    @Override
    protected StoneCardSaveResponse mapToDto(StoneCardEmbeddingEntity stoneCardEmbeddingEntity) {
        return new StoneCardSaveResponse(stoneCardEmbeddingEntity.getStoneId());
    }
}
