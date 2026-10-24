package lab.stoneshelter.exceptions;

public class StoneChatUnavailableException extends RuntimeException {
    public StoneChatUnavailableException() { super("Chat is unavailable. Restore history manually before retrying."); }
    public StoneChatUnavailableException(Throwable cause) { super("Chat is unavailable. Restore history manually before retrying.", cause); }
}
