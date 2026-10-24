package lab.stoneshelter.exceptions;

public class InvalidStoneSemanticSearchOutputException extends RuntimeException {
    private final String correction;
    public InvalidStoneSemanticSearchOutputException(String correction) { super("Invalid structured semantic-search output"); this.correction = correction; }
    public String getCorrection() { return correction; }
    public InvalidStoneSemanticSearchOutputException() { super("Invalid structured semantic-search output"); correction = null; }
    public InvalidStoneSemanticSearchOutputException(Throwable cause) { super("Invalid structured semantic-search output", cause); correction = null; }
}
