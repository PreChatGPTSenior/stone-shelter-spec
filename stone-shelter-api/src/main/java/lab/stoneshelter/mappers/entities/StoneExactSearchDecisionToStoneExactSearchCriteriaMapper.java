package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.shared.StoneExactSearchDecision;
import org.springframework.stereotype.Component;

@Component
public class StoneExactSearchDecisionToStoneExactSearchCriteriaMapper extends AbstractEntityMapper<StoneExactSearchDecision, StoneExactSearchCriteria> {
    @Override protected StoneExactSearchCriteria newEntity() { return new StoneExactSearchCriteria(); }
    @Override protected StoneExactSearchCriteria populateEntity(StoneExactSearchDecision source, StoneExactSearchCriteria target) {
        target.setStoneTypes(source.getCriteria().stoneTypes()); target.setStoneSizes(source.getCriteria().stoneSizes());
        target.setAvailability(source.getCriteria().availability());
        target.setAdmissionDateRanges(source.getCriteria().admissionDateRanges().stream()
                .map(range -> new StoneExactSearchCriteria.DateRange(range.startInclusive(), range.endExclusive())).toList());
        return target;
    }
}
