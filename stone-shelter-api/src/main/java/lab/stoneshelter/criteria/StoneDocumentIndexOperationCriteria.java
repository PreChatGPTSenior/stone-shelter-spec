package lab.stoneshelter.criteria;

import java.time.Instant;
import java.util.UUID;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;

public record StoneDocumentIndexOperationCriteria(UUID idempotencyKey, StoneDocumentIndexOperationKind kind,
        UUID ownerToken, Instant deadline) { }
