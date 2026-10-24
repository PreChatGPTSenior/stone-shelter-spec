package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneReservationEntity;
import lab.stoneshelter.shared.StoneReservationCreateRequest;
import org.springframework.stereotype.Component;

@Component
public final class StoneReservationCreateRequestToStoneReservationEntityMapper
        extends AbstractEntityMapper<StoneReservationCreateRequest, StoneReservationEntity> {
    @Override
    protected StoneReservationEntity newEntity() { return new StoneReservationEntity(); }

    @Override
    protected StoneReservationEntity populateEntity(StoneReservationCreateRequest source, StoneReservationEntity entity) {
        entity.setApplicantName(source.applicantName());
        entity.setContactDetails(source.contactDetails());
        return entity;
    }
}
