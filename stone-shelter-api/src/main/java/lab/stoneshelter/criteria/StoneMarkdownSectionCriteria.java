package lab.stoneshelter.criteria;

import lab.stoneshelter.enums.StoneType;

public class StoneMarkdownSectionCriteria {
    private String sectionPath;
    private StoneType stoneType;
    private String text;
    private String sourceVersion;
    private int fragmentOrder;

    public StoneMarkdownSectionCriteria() { }
    public String getSectionPath() { return sectionPath; }
    public void setSectionPath(String sectionPath) { this.sectionPath = sectionPath; }
    public StoneType getStoneType() { return stoneType; }
    public void setStoneType(StoneType stoneType) { this.stoneType = stoneType; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public int getFragmentOrder() { return fragmentOrder; }
    public void setFragmentOrder(int fragmentOrder) { this.fragmentOrder = fragmentOrder; }
}
