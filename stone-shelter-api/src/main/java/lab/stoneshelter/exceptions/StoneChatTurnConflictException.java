package lab.stoneshelter.exceptions;

public class StoneChatTurnConflictException extends RuntimeException {
    public StoneChatTurnConflictException() { super("Turn identity was already committed with a different payload."); }
}
