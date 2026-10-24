package lab.stoneshelter.exceptions;

public class StoneDocumentDependencyUnavailableException extends RuntimeException {
    public StoneDocumentDependencyUnavailableException(String message) {
        super(message);
    }

    public StoneDocumentDependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
