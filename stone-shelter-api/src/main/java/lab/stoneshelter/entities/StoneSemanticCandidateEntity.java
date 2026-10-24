package lab.stoneshelter.entities;

import java.util.List;
import lab.stoneshelter.criteria.StoneSemanticWish;

public class StoneSemanticCandidateEntity {
    private StoneEntity stone;
    private Double cardScore;
    private List<StoneSemanticRetrievalResultEntity.Passage> passages = List.of();
    private List<StoneSemanticWish> matchedWishes = List.of();
    private List<StoneSemanticWish> unmatchedWishes = List.of();
    private boolean cardSupported;
    private boolean contradicted;
    private Double supportingDocumentScore;
    private List<StoneSemanticEvidenceResultEntity.Judgement> evidence = List.of();

    public StoneSemanticCandidateEntity() {}
    public StoneEntity getStone() { return stone; }
    public void setStone(StoneEntity stone) { this.stone = stone; }
    public Double getCardScore() { return cardScore; }
    public void setCardScore(Double cardScore) { this.cardScore = cardScore; }
    public List<StoneSemanticRetrievalResultEntity.Passage> getPassages() { return passages; }
    public void setPassages(List<StoneSemanticRetrievalResultEntity.Passage> passages) { this.passages = passages; }
    public List<StoneSemanticWish> getMatchedWishes() { return matchedWishes; }
    public void setMatchedWishes(List<StoneSemanticWish> matchedWishes) { this.matchedWishes = matchedWishes; }
    public List<StoneSemanticWish> getUnmatchedWishes() { return unmatchedWishes; }
    public void setUnmatchedWishes(List<StoneSemanticWish> unmatchedWishes) { this.unmatchedWishes = unmatchedWishes; }
    public boolean getCardSupported() { return cardSupported; }
    public void setCardSupported(boolean cardSupported) { this.cardSupported = cardSupported; }
    public boolean getContradicted() { return contradicted; }
    public void setContradicted(boolean contradicted) { this.contradicted = contradicted; }
    public Double getSupportingDocumentScore() { return supportingDocumentScore; }
    public void setSupportingDocumentScore(Double supportingDocumentScore) { this.supportingDocumentScore = supportingDocumentScore; }
    public List<StoneSemanticEvidenceResultEntity.Judgement> getEvidence() { return evidence; }
    public void setEvidence(List<StoneSemanticEvidenceResultEntity.Judgement> evidence) { this.evidence = List.copyOf(evidence); }
}
