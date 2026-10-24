package lab.stoneshelter.exceptions;

public class StoneSemanticSearchLimitExceededException extends RuntimeException {
    public StoneSemanticSearchLimitExceededException() { super("The semantic turn exceeds its bounded evidence/context allowance"); }
}
