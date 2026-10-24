package lab.stoneshelter.exceptions;

public class StoneChatStreamCapacityExceededException extends RuntimeException {
    public StoneChatStreamCapacityExceededException() { super("Chat stream capacity is exhausted."); }
}
