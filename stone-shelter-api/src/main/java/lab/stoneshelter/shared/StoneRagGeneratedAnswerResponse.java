package lab.stoneshelter.shared;

import java.util.List;

public record StoneRagGeneratedAnswerResponse(String introduction, List<StoneRagStoneExplanationResponse> stones, String knowledge) {
    public StoneRagGeneratedAnswerResponse { stones = List.copyOf(stones); }
}
