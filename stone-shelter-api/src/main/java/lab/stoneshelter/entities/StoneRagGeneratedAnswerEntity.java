package lab.stoneshelter.entities;

import java.util.List;

public record StoneRagGeneratedAnswerEntity(String introduction, List<Explanation> stones, String knowledge) {
    public record Explanation(long id, String explanation) {}
    public StoneRagGeneratedAnswerEntity { stones = List.copyOf(stones); }
}
