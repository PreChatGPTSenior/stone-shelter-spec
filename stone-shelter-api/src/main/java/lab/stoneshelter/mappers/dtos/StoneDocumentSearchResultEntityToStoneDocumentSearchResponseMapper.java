package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneDocumentSearchResultEntity;
import lab.stoneshelter.shared.StoneDocumentSearchResponse;
import lab.stoneshelter.shared.StoneDocumentPassageResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper
        extends AbstractDtoMapper<StoneDocumentSearchResultEntity, StoneDocumentSearchResponse> {
    @Override protected StoneDocumentSearchResponse mapToDto(StoneDocumentSearchResultEntity source) {
        return new StoneDocumentSearchResponse(source.passages().stream().map(passage ->
                new StoneDocumentPassageResponse(passage.fragment().getSectionPath(), passage.fragment().getStoneType(),
                        passage.fragment().getText(), passage.score())).toList());
    }
}
