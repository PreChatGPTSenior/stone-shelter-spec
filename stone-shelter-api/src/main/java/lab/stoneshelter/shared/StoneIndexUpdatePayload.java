package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;

public class StoneIndexUpdatePayload {
    private Long stoneId;
    private String name;
    private StoneType stoneType;
    private StoneSize stoneSize;
    private String biography;
    private Long sourceVersion;

    public StoneIndexUpdatePayload() {}
    public Long getStoneId() { return stoneId; }
    public void setStoneId(Long stoneId) { this.stoneId = stoneId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StoneType getStoneType() { return stoneType; }
    public void setStoneType(StoneType stoneType) { this.stoneType = stoneType; }
    public StoneSize getStoneSize() { return stoneSize; }
    public void setStoneSize(StoneSize stoneSize) { this.stoneSize = stoneSize; }
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    public Long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(Long sourceVersion) { this.sourceVersion = sourceVersion; }
}
