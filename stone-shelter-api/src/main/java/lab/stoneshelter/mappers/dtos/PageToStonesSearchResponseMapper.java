package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.shared.StonesSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public final class PageToStonesSearchResponseMapper
        extends AbstractDtoMapper<Page<StoneEntity>, StonesSearchResponse> {
    private final StoneEntityToStoneSearchResponseMapper itemMapper;

    public PageToStonesSearchResponseMapper(StoneEntityToStoneSearchResponseMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    protected StonesSearchResponse mapToDto(Page<StoneEntity> page) {
        return new StonesSearchResponse(
                page.getContent().stream().map(itemMapper::toDto).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
