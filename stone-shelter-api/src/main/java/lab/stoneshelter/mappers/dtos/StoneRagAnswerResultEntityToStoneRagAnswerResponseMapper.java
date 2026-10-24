package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneRagAnswerResultEntity;
import lab.stoneshelter.shared.StoneRagAnswerResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper extends AbstractDtoMapper<StoneRagAnswerResultEntity, StoneRagAnswerResponse> {
    private final StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper answer;
    public StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper(StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper answer) { this.answer = answer; }
    @Override protected StoneRagAnswerResponse mapToDto(StoneRagAnswerResultEntity source) {
        return new StoneRagAnswerResponse(source.answer() == null ? null : answer.toDto(source.answer()), source.completion());
    }
}
