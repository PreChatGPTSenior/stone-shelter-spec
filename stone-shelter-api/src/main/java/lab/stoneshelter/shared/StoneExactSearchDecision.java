package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneExactSearchAction;

public class StoneExactSearchDecision {
    private StoneExactSearchAction action;
    private StoneExactSearchFilters criteria;
    private boolean resetSearch;
    private StoneExactSearchReference reference;
    private List<String> questions;
    private String reason;
    public StoneExactSearchDecision() {}
    public StoneExactSearchAction getAction() { return action; }
    public void setAction(StoneExactSearchAction action) { this.action = action; }
    public StoneExactSearchFilters getCriteria() { return criteria; }
    public void setCriteria(StoneExactSearchFilters criteria) { this.criteria = criteria; }
    public boolean getResetSearch() { return resetSearch; }
    public void setResetSearch(boolean resetSearch) { this.resetSearch = resetSearch; }
    public StoneExactSearchReference getReference() { return reference; }
    public void setReference(StoneExactSearchReference reference) { this.reference = reference; }
    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
