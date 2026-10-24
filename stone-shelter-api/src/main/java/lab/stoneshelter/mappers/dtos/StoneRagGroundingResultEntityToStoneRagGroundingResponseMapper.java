package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneRagGroundingResultEntity;
import lab.stoneshelter.shared.StoneRagGroundingResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper extends AbstractDtoMapper<StoneRagGroundingResultEntity, StoneRagGroundingResponse> {
    @Override protected StoneRagGroundingResponse mapToDto(StoneRagGroundingResultEntity source) { return new StoneRagGroundingResponse(source.valid(), source.reason()); }
}
