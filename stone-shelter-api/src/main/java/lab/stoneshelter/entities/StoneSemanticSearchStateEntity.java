package lab.stoneshelter.entities;

import java.util.List;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.enums.StoneSemanticSearchMode;

public class StoneSemanticSearchStateEntity {
    private StoneExactSearchCriteria exact;
    private List<StoneSemanticWish> wishes = List.of();
    private StoneSemanticSearchMode mode;
    private boolean actualSelection;

    public StoneSemanticSearchStateEntity() {}
    public StoneExactSearchCriteria getExact() { return exact; }
    public void setExact(StoneExactSearchCriteria exact) { this.exact = exact; }
    public List<StoneSemanticWish> getWishes() { return wishes; }
    public void setWishes(List<StoneSemanticWish> wishes) { this.wishes = wishes; }
    public StoneSemanticSearchMode getMode() { return mode; }
    public void setMode(StoneSemanticSearchMode mode) { this.mode = mode; }
    public boolean getActualSelection() { return actualSelection; }
    public void setActualSelection(boolean actualSelection) { this.actualSelection = actualSelection; }
}
