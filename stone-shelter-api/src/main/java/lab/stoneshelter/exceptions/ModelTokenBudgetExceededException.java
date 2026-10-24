package lab.stoneshelter.exceptions;

import java.time.Instant;

public class ModelTokenBudgetExceededException extends RuntimeException {
    private final Instant retryAt;
    public ModelTokenBudgetExceededException(Instant retryAt) { super("Daily model token budget is exhausted."); this.retryAt = retryAt; }
    public Instant getRetryAt() { return retryAt; }
}
