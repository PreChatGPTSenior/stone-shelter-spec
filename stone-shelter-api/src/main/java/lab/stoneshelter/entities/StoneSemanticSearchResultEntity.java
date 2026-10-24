package lab.stoneshelter.entities;

import java.util.List;
import lab.stoneshelter.enums.StoneSemanticSearchMode;

public class StoneSemanticSearchResultEntity {
    private StoneSemanticSearchMode mode = StoneSemanticSearchMode.NO_MATCH;
    private long foundCount;
    private boolean hasMore;
    private String text;
    private List<StoneSemanticCandidateEntity> candidates = List.of();
    private StoneSemanticSearchStateEntity state;

    public StoneSemanticSearchResultEntity() {}
    public StoneSemanticSearchMode getMode() { return mode; }
    public void setMode(StoneSemanticSearchMode mode) { this.mode = mode; }
    public long getFoundCount() { return foundCount; }
    public void setFoundCount(long foundCount) { this.foundCount = foundCount; }
    public boolean getHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<StoneSemanticCandidateEntity> getCandidates() { return candidates; }
    public void setCandidates(List<StoneSemanticCandidateEntity> candidates) { this.candidates = candidates; }
    public StoneSemanticSearchStateEntity getState() { return state; }
    public void setState(StoneSemanticSearchStateEntity state) { this.state = state; }
}
