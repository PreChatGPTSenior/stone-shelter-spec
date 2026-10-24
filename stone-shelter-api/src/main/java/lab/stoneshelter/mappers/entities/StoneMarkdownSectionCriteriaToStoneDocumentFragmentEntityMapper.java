package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.criteria.StoneMarkdownSectionCriteria;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import org.springframework.stereotype.Component;

@Component
public class StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper
        extends AbstractEntityMapper<StoneMarkdownSectionCriteria, StoneDocumentFragmentEntity> {
    @Override
    protected StoneDocumentFragmentEntity newEntity() { return new StoneDocumentFragmentEntity(); }

    @Override
    protected StoneDocumentFragmentEntity populateEntity(StoneMarkdownSectionCriteria source, StoneDocumentFragmentEntity entity) {
        entity.setDocumentKey(StoneDocumentIndexEntity.DOCUMENT_KEY);
        entity.setLanguage(StoneDocumentIndexEntity.LANGUAGE);
        entity.setSourceVersion(source.getSourceVersion());
        entity.setSectionPath(source.getSectionPath());
        entity.setStoneType(source.getStoneType());
        entity.setText(source.getText());
        entity.setFragmentOrder(source.getFragmentOrder());
        return entity;
    }
}
