package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StoneUpdateRequest;
import org.springframework.stereotype.Component;

@Component
public final class StoneUpdateRequestToStoneEntityMapper extends AbstractEntityMapper<StoneUpdateRequest, StoneEntity> {
    @Override
    protected StoneEntity newEntity() {
        return new StoneEntity();
    }

    @Override
    protected StoneEntity populateEntity(StoneUpdateRequest request, StoneEntity entity) {
        entity.setName(request.getName());
        entity.setPhoto(request.getPhoto());
        entity.setStoneType(request.getStoneType());
        entity.setBiography(request.getBiography());
        entity.setAdoptionStatus(request.getAdoptionStatus());
        entity.setAdmissionDate(request.getAdmissionDate());
        entity.setStoneSize(request.getStoneSize());
        return entity;
    }
}
