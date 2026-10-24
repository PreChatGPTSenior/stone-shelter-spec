package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.shared.StoneSemanticSearchDecision;
import lab.stoneshelter.shared.StoneExactSearchDecision;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticSearchDecisionToStoneExactSearchDecisionMapper extends AbstractDtoMapper<StoneSemanticSearchDecision, StoneExactSearchDecision> {
    @Override protected StoneExactSearchDecision mapToDto(StoneSemanticSearchDecision source) {
        StoneExactSearchDecision target = new StoneExactSearchDecision();
        target.setAction(source.getAction()); target.setCriteria(source.getCriteria()); target.setResetSearch(source.getResetSearch());
        target.setReference(source.getReference()); target.setQuestions(source.getQuestions()); target.setReason(source.getReason());
        return target;
    }
}
