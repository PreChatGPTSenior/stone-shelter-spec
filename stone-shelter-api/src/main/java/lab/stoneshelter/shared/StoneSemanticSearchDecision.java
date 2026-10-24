package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneExactSearchAction;

public class StoneSemanticSearchDecision {
    public record Wishes(String color, String use, List<StoneSemanticWishRequest> other) {}
    private StoneExactSearchAction action;
    private StoneExactSearchFilters criteria;
    private boolean resetSearch;
    private StoneExactSearchReference reference;
    private List<String> questions;
    private String reason;
    private Wishes wishes;
    public StoneSemanticSearchDecision() {}
    public StoneExactSearchAction getAction() { return action; }
    public void setAction(StoneExactSearchAction value) { action = value; }
    public StoneExactSearchFilters getCriteria() { return criteria; }
    public void setCriteria(StoneExactSearchFilters value) { criteria = value; }
    public boolean getResetSearch() { return resetSearch; }
    public void setResetSearch(boolean value) { resetSearch = value; }
    public StoneExactSearchReference getReference() { return reference; }
    public void setReference(StoneExactSearchReference value) { reference = value; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> value) { questions = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Wishes getWishes() { return wishes; }
    public void setWishes(Wishes value) { wishes = value; }
}
