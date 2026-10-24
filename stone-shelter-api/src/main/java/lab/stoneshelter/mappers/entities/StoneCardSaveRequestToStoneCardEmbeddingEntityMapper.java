package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import org.springframework.stereotype.Component;

@Component
public class StoneCardSaveRequestToStoneCardEmbeddingEntityMapper
        extends AbstractEntityMapper<StoneCardSaveRequest, StoneCardEmbeddingEntity> {
    @Override
    protected StoneCardEmbeddingEntity newEntity() { return new StoneCardEmbeddingEntity(); }

    @Override
    protected StoneCardEmbeddingEntity populateEntity(StoneCardSaveRequest stoneCardSaveRequest,
            StoneCardEmbeddingEntity stoneCardEmbeddingEntity) {
        stoneCardEmbeddingEntity.setStoneId(stoneCardSaveRequest.getStoneId());
        stoneCardEmbeddingEntity.setText("Name: " + stoneCardSaveRequest.getName()
                + "\nStone type: " + stoneCardSaveRequest.getStoneType().name()
                + "\nSize: " + stoneCardSaveRequest.getStoneSize().name()
                + "\nBiography: " + (stoneCardSaveRequest.getBiography() == null ? "" : stoneCardSaveRequest.getBiography()));
        return stoneCardEmbeddingEntity;
    }
}
