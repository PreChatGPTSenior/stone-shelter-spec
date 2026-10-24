package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StoneIndexUpdatePayload;
import org.springframework.stereotype.Component;

@Component
public class StoneEntityToStoneIndexUpdatePayloadMapper extends AbstractDtoMapper<StoneEntity, StoneIndexUpdatePayload> {
    public StoneIndexUpdatePayload toDto(StoneEntity stoneEntity, long sourceVersion) {
        StoneIndexUpdatePayload stoneIndexUpdatePayload = toDto(stoneEntity);
        stoneIndexUpdatePayload.setSourceVersion(sourceVersion);
        return stoneIndexUpdatePayload;
    }

    @Override
    protected StoneIndexUpdatePayload mapToDto(StoneEntity stoneEntity) {
        StoneIndexUpdatePayload stoneIndexUpdatePayload = new StoneIndexUpdatePayload();
        stoneIndexUpdatePayload.setStoneId(stoneEntity.getId());
        stoneIndexUpdatePayload.setName(stoneEntity.getName());
        stoneIndexUpdatePayload.setStoneType(stoneEntity.getStoneType());
        stoneIndexUpdatePayload.setStoneSize(stoneEntity.getStoneSize());
        stoneIndexUpdatePayload.setBiography(stoneEntity.getBiography());
        return stoneIndexUpdatePayload;
    }
}
