package lab.stoneshelter.entities;

import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;

/** Same per-turn caches/counters as semantic selection; never persisted. */
public class StoneRagExecutionEntity extends StoneSemanticSearchExecutionEntity {
    public StoneRagExecutionEntity() {}
    public void consumeGenerationCall(int maximum) {
        if (getGenerationCalls() >= maximum) throw new StoneRagAnswerLimitExceededException();
        setGenerationCalls(getGenerationCalls() + 1);
    }
}
