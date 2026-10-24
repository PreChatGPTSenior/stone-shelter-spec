package lab.stoneshelter.mappers.entities;

import java.time.Instant;
import lab.stoneshelter.criteria.StoneDocumentIndexOperationCriteria;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneDocumentIndexOperationEntity;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import org.springframework.stereotype.Component;

@Component
public class StoneDocumentIndexOperationCriteriaToStoneDocumentIndexOperationEntityMapper
        extends AbstractEntityMapper<StoneDocumentIndexOperationCriteria, StoneDocumentIndexOperationEntity> {
    @Override protected StoneDocumentIndexOperationEntity newEntity() { return new StoneDocumentIndexOperationEntity(); }
    @Override protected StoneDocumentIndexOperationEntity populateEntity(StoneDocumentIndexOperationCriteria source,
            StoneDocumentIndexOperationEntity entity) {
        entity.setIdempotencyKey(source.idempotencyKey());
        entity.setDocumentKey(StoneDocumentIndexEntity.DOCUMENT_KEY);
        entity.setKind(source.kind());
        entity.setOwnerToken(source.ownerToken());
        entity.setDeadline(source.deadline());
        entity.setStartedAt(Instant.now());
        entity.setState(StoneDocumentIndexOperationState.RUNNING);
        return entity;
    }
}
