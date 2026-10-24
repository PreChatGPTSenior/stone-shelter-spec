package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneSemanticEvidenceResultEntity;
import lab.stoneshelter.shared.StoneSemanticEvidenceResponse;
import lab.stoneshelter.shared.StoneSemanticWishEvidenceResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper
        extends AbstractDtoMapper<StoneSemanticEvidenceResultEntity, StoneSemanticEvidenceResponse> {
    @Override
    protected StoneSemanticEvidenceResponse mapToDto(StoneSemanticEvidenceResultEntity stoneSemanticEvidenceResultEntity) {
        return new StoneSemanticEvidenceResponse(stoneSemanticEvidenceResultEntity.judgements().stream()
                .map(judgement -> new StoneSemanticWishEvidenceResponse(judgement.key(), judgement.sourceId(), judgement.verdict(), judgement.quote())).toList());
    }
}
