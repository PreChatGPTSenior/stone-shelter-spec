package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StoneResponse;
import org.springframework.stereotype.Component;

@Component
public final class StoneEntityToStoneResponseMapper extends AbstractDtoMapper<StoneEntity, StoneResponse> {

    private final StonePhotoEntityToStonePhotoResponseMapper photoMapper;
    public StoneEntityToStoneResponseMapper(StonePhotoEntityToStonePhotoResponseMapper photoMapper) { this.photoMapper = photoMapper; }

    @Override
    protected StoneResponse mapToDto(StoneEntity entity) {
        var dto = new StoneResponse();
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
