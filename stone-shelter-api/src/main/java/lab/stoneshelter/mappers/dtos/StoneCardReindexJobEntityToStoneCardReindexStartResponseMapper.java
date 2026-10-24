package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.shared.StoneCardReindexStartResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneCardReindexJobEntityToStoneCardReindexStartResponseMapper
        extends AbstractDtoMapper<StoneCardReindexJobEntity, StoneCardReindexStartResponse> {
    @Override
    protected StoneCardReindexStartResponse mapToDto(StoneCardReindexJobEntity stoneCardReindexJobEntity) {
        return new StoneCardReindexStartResponse(stoneCardReindexJobEntity.getJobId(), stoneCardReindexJobEntity.getStatus());
    }
}
