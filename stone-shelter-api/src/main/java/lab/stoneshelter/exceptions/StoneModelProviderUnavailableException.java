package lab.stoneshelter.exceptions;

import java.util.UUID;

/** An actual guarded provider phase failed; ledger/control failures use their own path. */
public class StoneModelProviderUnavailableException extends StoneChatUnavailableException {
    private final UUID invocationId;
    public StoneModelProviderUnavailableException(UUID invocationId, Throwable cause) { super(cause); this.invocationId = invocationId; }
    public UUID getInvocationId() { return invocationId; }
}
