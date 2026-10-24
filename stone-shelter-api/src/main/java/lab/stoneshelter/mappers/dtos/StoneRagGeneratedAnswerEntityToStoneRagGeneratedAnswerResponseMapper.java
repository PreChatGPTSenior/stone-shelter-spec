package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneRagGeneratedAnswerEntity;
import lab.stoneshelter.shared.StoneRagGeneratedAnswerResponse;
import lab.stoneshelter.shared.StoneRagStoneExplanationResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper extends AbstractDtoMapper<StoneRagGeneratedAnswerEntity, StoneRagGeneratedAnswerResponse> {
    @Override protected StoneRagGeneratedAnswerResponse mapToDto(StoneRagGeneratedAnswerEntity source) {
        return new StoneRagGeneratedAnswerResponse(source.introduction(), source.stones().stream()
                .map(stone -> new StoneRagStoneExplanationResponse(stone.id(), stone.explanation())).toList(), source.knowledge());
    }
}
