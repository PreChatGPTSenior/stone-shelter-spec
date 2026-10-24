package lab.stoneshelter.exceptions;

public class OutboxValidationException extends OutboxWriteException {
    public OutboxValidationException(String message) { super(message); }
    public OutboxValidationException(String message, Throwable cause) { super(message, cause); }
}
