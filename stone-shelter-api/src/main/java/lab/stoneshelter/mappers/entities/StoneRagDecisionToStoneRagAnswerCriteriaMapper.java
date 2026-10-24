package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.criteria.StoneRagAnswerCriteria;
import lab.stoneshelter.shared.StoneRagDecision;
import org.springframework.stereotype.Component;

@Component
public class StoneRagDecisionToStoneRagAnswerCriteriaMapper extends AbstractEntityMapper<StoneRagDecision, StoneRagAnswerCriteria> {
    @Override protected StoneRagAnswerCriteria newEntity() { return new StoneRagAnswerCriteria(); }
    @Override protected StoneRagAnswerCriteria populateEntity(StoneRagDecision source, StoneRagAnswerCriteria target) {
        target.setMode(source.getMode()); target.setQuestion(source.getTopic()); return target;
    }
}
