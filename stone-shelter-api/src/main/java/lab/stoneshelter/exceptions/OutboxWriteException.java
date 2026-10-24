package lab.stoneshelter.exceptions;

public class OutboxWriteException extends RuntimeException {
    public OutboxWriteException(String message) { super(message); }
    public OutboxWriteException(String message, Throwable cause) { super(message, cause); }
}
