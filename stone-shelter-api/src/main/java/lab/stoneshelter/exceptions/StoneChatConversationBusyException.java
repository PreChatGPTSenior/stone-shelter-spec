package lab.stoneshelter.exceptions;

public class StoneChatConversationBusyException extends RuntimeException {
    public StoneChatConversationBusyException() { super("Conversation is processing another turn."); }
}
