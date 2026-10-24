package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;

public class StoneSemanticCandidateResponse {
    private long id;
    private String name;
    private StoneType stoneType;
    private StoneSize stoneSize;
    private String biography;
    private Double cardScore;
    private boolean cardSupported;
    private Double supportingDocumentScore;
    private List<StoneSemanticWishResponse> matchedWishes = List.of();
    private List<StoneSemanticWishResponse> unmatchedWishes = List.of();
    private List<StoneSemanticSourceResponse> sources = List.of();
    private List<StoneSemanticWishEvidenceResponse> evidence = List.of();

    public StoneSemanticCandidateResponse() {}
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StoneType getStoneType() { return stoneType; }
    public void setStoneType(StoneType stoneType) { this.stoneType = stoneType; }
    public StoneSize getStoneSize() { return stoneSize; }
    public void setStoneSize(StoneSize stoneSize) { this.stoneSize = stoneSize; }
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    public Double getCardScore() { return cardScore; }
    public void setCardScore(Double cardScore) { this.cardScore = cardScore; }
    public boolean getCardSupported() { return cardSupported; }
    public void setCardSupported(boolean cardSupported) { this.cardSupported = cardSupported; }
    public Double getSupportingDocumentScore() { return supportingDocumentScore; }
    public void setSupportingDocumentScore(Double supportingDocumentScore) { this.supportingDocumentScore = supportingDocumentScore; }
    public List<StoneSemanticWishResponse> getMatchedWishes() { return matchedWishes; }
    public void setMatchedWishes(List<StoneSemanticWishResponse> matchedWishes) { this.matchedWishes = matchedWishes; }
    public List<StoneSemanticWishResponse> getUnmatchedWishes() { return unmatchedWishes; }
    public void setUnmatchedWishes(List<StoneSemanticWishResponse> unmatchedWishes) { this.unmatchedWishes = unmatchedWishes; }
    public List<StoneSemanticSourceResponse> getSources() { return sources; }
    public void setSources(List<StoneSemanticSourceResponse> sources) { this.sources = sources; }
    public List<StoneSemanticWishEvidenceResponse> getEvidence() { return evidence; }
    public void setEvidence(List<StoneSemanticWishEvidenceResponse> evidence) { this.evidence = List.copyOf(evidence); }
}
