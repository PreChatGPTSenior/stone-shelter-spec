package lab.stoneshelter.exceptions;

public class StoneRagAnswerLimitExceededException extends RuntimeException {
    public StoneRagAnswerLimitExceededException() { super("The answer exceeds its bounded generation/context allowance"); }
}
