package lab.stoneshelter.criteria;

import java.util.List;
import lab.stoneshelter.enums.StoneType;

public record StoneRagKnowledgeCriteria(String question, List<StoneType> stoneTypes, Long stoneId) {
    public StoneRagKnowledgeCriteria { stoneTypes = List.copyOf(stoneTypes); }
}
