package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneDocumentIndexOperationEntity;
import lab.stoneshelter.shared.StoneEncyclopediaIndexResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneDocumentIndexOperationEntityToStoneEncyclopediaIndexResponseMapper
        extends AbstractDtoMapper<StoneDocumentIndexOperationEntity, StoneEncyclopediaIndexResponse> {
    @Override protected StoneEncyclopediaIndexResponse mapToDto(StoneDocumentIndexOperationEntity source) {
        return new StoneEncyclopediaIndexResponse(source.getDocumentKey(), source.getSourceVersion(),
                source.getIndexedFragments(), source.getOutcome());
    }
}
