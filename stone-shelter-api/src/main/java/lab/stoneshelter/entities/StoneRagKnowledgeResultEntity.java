package lab.stoneshelter.entities;

import java.util.List;

public class StoneRagKnowledgeResultEntity {
    private StoneEntity stone;
    private List<StoneSemanticRetrievalResultEntity.Passage> passages = List.of();
    public StoneRagKnowledgeResultEntity() {}
    public StoneEntity getStone() { return stone; }
    public void setStone(StoneEntity value) { stone = value; }
    public List<StoneSemanticRetrievalResultEntity.Passage> getPassages() { return passages; }
    public void setPassages(List<StoneSemanticRetrievalResultEntity.Passage> value) { passages = value; }
}
