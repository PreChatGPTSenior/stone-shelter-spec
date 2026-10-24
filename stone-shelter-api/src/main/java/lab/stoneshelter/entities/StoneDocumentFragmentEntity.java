package lab.stoneshelter.entities;

import lab.stoneshelter.enums.StoneType;

public class StoneDocumentFragmentEntity {
    private String documentKey;
    private String sourceVersion;
    private String embeddingProfile;
    private String language;
    private StoneType stoneType;
    private String sectionPath;
    private int fragmentOrder;
    private String text;
    private double[] embedding;

    public StoneDocumentFragmentEntity() {
    }

    public String getDocumentKey() { return documentKey; }
    public void setDocumentKey(String documentKey) { this.documentKey = documentKey; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public String getEmbeddingProfile() { return embeddingProfile; }
    public void setEmbeddingProfile(String embeddingProfile) { this.embeddingProfile = embeddingProfile; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public StoneType getStoneType() { return stoneType; }
    public void setStoneType(StoneType stoneType) { this.stoneType = stoneType; }
    public String getSectionPath() { return sectionPath; }
    public void setSectionPath(String sectionPath) { this.sectionPath = sectionPath; }
    public int getFragmentOrder() { return fragmentOrder; }
    public void setFragmentOrder(int fragmentOrder) { this.fragmentOrder = fragmentOrder; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public double[] getEmbedding() { return embedding; }
    public void setEmbedding(double[] embedding) { this.embedding = embedding; }
}
