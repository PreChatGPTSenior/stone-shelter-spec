package lab.stoneshelter.entities;

public class StoneCardEmbeddingEntity {
    private Long stoneId;
    private String text;
    private String embeddingProfile;
    private double[] embedding;

    public StoneCardEmbeddingEntity() {}
    public Long getStoneId() { return stoneId; }
    public void setStoneId(Long stoneId) { this.stoneId = stoneId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getEmbeddingProfile() { return embeddingProfile; }
    public void setEmbeddingProfile(String embeddingProfile) { this.embeddingProfile = embeddingProfile; }
    public double[] getEmbedding() { return embedding == null ? null : embedding.clone(); }
    public void setEmbedding(double[] embedding) { this.embedding = embedding == null ? null : embedding.clone(); }
}
