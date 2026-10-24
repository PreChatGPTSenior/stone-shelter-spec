package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.enums.StoneExactSearchAction;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.shared.StoneRagDecision;
import lab.stoneshelter.shared.StoneSemanticSearchDecision;
import org.springframework.stereotype.Component;

@Component
public class StoneRagDecisionToStoneSemanticSearchDecisionMapper extends AbstractDtoMapper<StoneRagDecision, StoneSemanticSearchDecision> {
    @Override protected StoneSemanticSearchDecision mapToDto(StoneRagDecision source) {
        StoneSemanticSearchDecision target = new StoneSemanticSearchDecision();
        target.setAction(source.getMode() == StoneRagMode.SELECTION ? StoneExactSearchAction.SEARCH
                : source.getMode() == StoneRagMode.CLARIFY ? StoneExactSearchAction.CLARIFY : StoneExactSearchAction.UNSUPPORTED);
        target.setCriteria(source.getCriteria()); target.setResetSearch(source.getResetSearch()); target.setReference(source.getReference());
        target.setQuestions(source.getQuestions()); target.setReason(source.getReason()); target.setWishes(source.getWishes()); return target;
    }
}
