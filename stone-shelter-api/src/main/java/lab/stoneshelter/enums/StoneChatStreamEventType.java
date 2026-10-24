package lab.stoneshelter.enums;

public enum StoneChatStreamEventType {
    STARTED("started"), TEXT_DELTA("text-delta"), COMPLETED("completed"), ERROR("error");
    private final String wireName;
    StoneChatStreamEventType(String wireName) { this.wireName = wireName; }
    public String getWireName() { return wireName; }
}
