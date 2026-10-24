package lab.stoneshelter.criteria;

import java.time.Instant;
import lab.stoneshelter.enums.ModelAdmissionMode;

public record StoneDocumentEmbeddingCriteria(String text, boolean query, Instant deadline, ModelAdmissionMode admissionMode) {
    public StoneDocumentEmbeddingCriteria(String text, boolean query, Instant deadline) {
        this(text, query, deadline, query ? ModelAdmissionMode.CUSTOMER : ModelAdmissionMode.IMPORT);
    }
}
