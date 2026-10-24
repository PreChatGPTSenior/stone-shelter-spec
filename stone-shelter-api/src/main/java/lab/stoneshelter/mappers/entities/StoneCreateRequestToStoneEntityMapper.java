package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StoneCreateRequest;
import org.springframework.stereotype.Component;

@Component
public final class StoneCreateRequestToStoneEntityMapper extends AbstractEntityMapper<StoneCreateRequest, StoneEntity> {
    @Override
    protected StoneEntity newEntity() {
        return new StoneEntity();
    }

    @Override
    protected StoneEntity populateEntity(StoneCreateRequest request, StoneEntity entity) {
        entity.setName(request.getName());
        entity.setPhoto(request.getPhoto());
        entity.setStoneType(request.getStoneType());
        entity.setBiography(request.getBiography());
        entity.setAdoptionStatus(request.getAdoptionStatus());
        entity.setStoneSize(request.getStoneSize());
        return entity;
    }
}
