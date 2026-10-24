package lab.stoneshelter.exceptions;

import java.time.Instant;

public class ModelAdmissionDeferredException extends RuntimeException {
    private final Instant retryAt;
    public ModelAdmissionDeferredException(Instant retryAt) { super("Model admission is deferred."); this.retryAt = retryAt; }
    public Instant getRetryAt() { return retryAt; }
}
