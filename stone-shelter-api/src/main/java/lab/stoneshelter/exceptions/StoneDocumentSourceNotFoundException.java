package lab.stoneshelter.exceptions;

public class StoneDocumentSourceNotFoundException extends RuntimeException {
    public StoneDocumentSourceNotFoundException() { super("The configured Markdown source was not found."); }
}
