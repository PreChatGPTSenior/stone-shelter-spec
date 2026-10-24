package lab.stoneshelter.exceptions;

public class StoneChatConversationNotFoundException extends RuntimeException {
    public StoneChatConversationNotFoundException() { super("Conversation not found."); }
}
