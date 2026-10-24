package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StoneCreateResponse;
import org.springframework.stereotype.Component;

@Component
public final class StoneEntityToStoneCreateResponseMapper extends AbstractDtoMapper<StoneEntity, StoneCreateResponse> {

    private final StonePhotoEntityToStonePhotoResponseMapper photoMapper;
    public StoneEntityToStoneCreateResponseMapper(StonePhotoEntityToStonePhotoResponseMapper photoMapper) { this.photoMapper = photoMapper; }

    @Override
    protected StoneCreateResponse mapToDto(StoneEntity entity) {
        var dto = new StoneCreateResponse();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPhotos(entity.getPhotos().stream().map(photoMapper::toDto).toList());
        dto.setPhoto(dto.getPhotos().isEmpty() ? entity.getPhoto() : dto.getPhotos().getFirst().url());
        dto.setStoneType(entity.getStoneType());
        dto.setBiography(entity.getBiography());
        dto.setAdoptionStatus(entity.getAdoptionStatus());
        dto.setAdmissionDate(entity.getAdmissionDate());
        dto.setStoneSize(entity.getStoneSize());
        return dto;
    }
}
