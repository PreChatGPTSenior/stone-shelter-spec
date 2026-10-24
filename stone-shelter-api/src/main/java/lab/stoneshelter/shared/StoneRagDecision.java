package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneRagMode;

public class StoneRagDecision {
    private StoneRagMode mode;
    private StoneExactSearchFilters criteria;
    private boolean resetSearch;
    private StoneExactSearchReference reference;
    private List<String> questions;
    private String reason;
    private StoneSemanticSearchDecision.Wishes wishes;
    private String topic;
    public StoneRagDecision() {}
    public StoneRagMode getMode() { return mode; }
    public void setMode(StoneRagMode value) { mode = value; }
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
    public StoneSemanticSearchDecision.Wishes getWishes() { return wishes; }
    public void setWishes(StoneSemanticSearchDecision.Wishes value) { wishes = value; }
    public String getTopic() { return topic; }
    public void setTopic(String value) { topic = value; }
}
