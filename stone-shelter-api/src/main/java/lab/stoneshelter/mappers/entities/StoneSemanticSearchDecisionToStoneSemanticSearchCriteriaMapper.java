package lab.stoneshelter.mappers.entities;

import java.util.ArrayList;
import java.util.List;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.shared.StoneSemanticSearchDecision;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticSearchDecisionToStoneSemanticSearchCriteriaMapper extends AbstractEntityMapper<StoneSemanticSearchDecision, StoneSemanticSearchCriteria> {
    @Override protected StoneSemanticSearchCriteria newEntity() { return new StoneSemanticSearchCriteria(new StoneExactSearchCriteria(), List.of()); }
    @Override protected StoneSemanticSearchCriteria populateEntity(StoneSemanticSearchDecision source, StoneSemanticSearchCriteria target) {
        StoneExactSearchCriteria exact = new StoneExactSearchCriteria();
        exact.setStoneTypes(source.getCriteria().stoneTypes()); exact.setStoneSizes(source.getCriteria().stoneSizes());
        exact.setAvailability(source.getCriteria().availability());
        exact.setAdmissionDateRanges(source.getCriteria().admissionDateRanges().stream()
                .map(range -> new StoneExactSearchCriteria.DateRange(range.startInclusive(), range.endExclusive())).toList());
        List<StoneSemanticWish> wishes = new ArrayList<>();
        if (source.getWishes().color() != null) wishes.add(new StoneSemanticWish("color", source.getWishes().color()));
        if (source.getWishes().use() != null) wishes.add(new StoneSemanticWish("use", source.getWishes().use()));
        source.getWishes().other().forEach(wish -> wishes.add(new StoneSemanticWish(wish.key(), wish.text())));
        return new StoneSemanticSearchCriteria(exact, wishes);
    }
}
