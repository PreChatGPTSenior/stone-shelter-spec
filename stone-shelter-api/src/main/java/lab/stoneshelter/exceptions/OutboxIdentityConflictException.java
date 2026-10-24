package lab.stoneshelter.exceptions;

import java.util.UUID;

public class OutboxIdentityConflictException extends OutboxWriteException {
    public OutboxIdentityConflictException(UUID eventId) {
        super("The event ID already identifies a different event type or schema version: " + eventId);
    }
}
