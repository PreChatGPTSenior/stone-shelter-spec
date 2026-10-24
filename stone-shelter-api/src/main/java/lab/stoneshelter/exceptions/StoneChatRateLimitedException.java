package lab.stoneshelter.exceptions;

public class StoneChatRateLimitedException extends RuntimeException {
    private final long retryAfter;
    public StoneChatRateLimitedException(long retryAfter) { super("Chat request quota exhausted."); this.retryAfter = retryAfter; }
    public long getRetryAfter() { return retryAfter; }
}
