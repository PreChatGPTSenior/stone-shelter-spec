package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneSemanticSearchMode;

public class StoneSemanticSearchResponse {
    private StoneSemanticSearchMode mode;
    private long foundCount;
    private boolean hasMore;
    private String text;
    private List<StoneSemanticCandidateResponse> candidates = List.of();
    private StoneSemanticSearchStateResponse state;

    public StoneSemanticSearchResponse() {}
    public StoneSemanticSearchMode getMode() { return mode; }
    public void setMode(StoneSemanticSearchMode mode) { this.mode = mode; }
    public long getFoundCount() { return foundCount; }
    public void setFoundCount(long foundCount) { this.foundCount = foundCount; }
    public boolean getHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<StoneSemanticCandidateResponse> getCandidates() { return candidates; }
    public void setCandidates(List<StoneSemanticCandidateResponse> candidates) { this.candidates = candidates; }
    public StoneSemanticSearchStateResponse getState() { return state; }
    public void setState(StoneSemanticSearchStateResponse state) { this.state = state; }
}
