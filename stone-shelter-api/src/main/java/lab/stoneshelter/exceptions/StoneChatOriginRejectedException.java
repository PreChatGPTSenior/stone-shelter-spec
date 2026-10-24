package lab.stoneshelter.exceptions;

public class StoneChatOriginRejectedException extends RuntimeException {
    public StoneChatOriginRejectedException() { super("Chat source origin is not allowed."); }
}
