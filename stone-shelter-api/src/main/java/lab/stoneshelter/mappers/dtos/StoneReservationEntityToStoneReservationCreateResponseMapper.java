package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneReservationEntity;
import lab.stoneshelter.shared.StoneReservationCreateResponse;
import org.springframework.stereotype.Component;

@Component
public final class StoneReservationEntityToStoneReservationCreateResponseMapper
        extends AbstractDtoMapper<StoneReservationEntity, StoneReservationCreateResponse> {
    @Override
    protected StoneReservationCreateResponse mapToDto(StoneReservationEntity source) {
        return new StoneReservationCreateResponse(source.getId(), source.getStone().getId(),
                source.getStone().getAdoptionStatus(), source.getCreatedAt());
    }
}
