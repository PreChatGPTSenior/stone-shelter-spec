package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;

public class StoneRagCandidateRequest {
    private long id;
    private String name;
    private StoneType stoneType;
    private StoneSize stoneSize;
    private String biography;
    private List<StoneSemanticWishRequest> matched = List.of();
    private List<StoneSemanticWishRequest> unmatched = List.of();
    public StoneRagCandidateRequest() {}
    public long getId() { return id; }
    public void setId(long value) { id = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public StoneType getStoneType() { return stoneType; }
    public void setStoneType(StoneType value) { stoneType = value; }
    public StoneSize getStoneSize() { return stoneSize; }
    public void setStoneSize(StoneSize value) { stoneSize = value; }
    public String getBiography() { return biography; }
    public void setBiography(String value) { biography = value; }
    public List<StoneSemanticWishRequest> getMatched() { return matched; }
    public void setMatched(List<StoneSemanticWishRequest> value) { matched = value; }
    public List<StoneSemanticWishRequest> getUnmatched() { return unmatched; }
    public void setUnmatched(List<StoneSemanticWishRequest> value) { unmatched = value; }
}
