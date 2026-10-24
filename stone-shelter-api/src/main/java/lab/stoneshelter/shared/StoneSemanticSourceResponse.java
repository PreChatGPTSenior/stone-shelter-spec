package lab.stoneshelter.shared;

import lab.stoneshelter.enums.StoneType;

public class StoneSemanticSourceResponse {
    private String id;
    private String text;
    private StoneType stoneType;
    private Provenance provenance;
    private double score;

    public StoneSemanticSourceResponse() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public StoneType getStoneType() { return stoneType; }
    public void setStoneType(StoneType stoneType) { this.stoneType = stoneType; }
    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public record Provenance(String documentKey, String sourceVersion, String sectionPath, int fragmentOrder) {}
}
