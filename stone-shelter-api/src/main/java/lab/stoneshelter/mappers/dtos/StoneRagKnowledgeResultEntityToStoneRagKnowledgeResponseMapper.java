package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneRagKnowledgeResultEntity;
import lab.stoneshelter.shared.StoneRagCandidateRequest;
import lab.stoneshelter.shared.StoneRagKnowledgeResponse;
import lab.stoneshelter.shared.StoneRagSourceRequest;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import org.springframework.stereotype.Component;

@Component
public class StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper extends AbstractDtoMapper<StoneRagKnowledgeResultEntity, StoneRagKnowledgeResponse> {
    @Override protected StoneRagKnowledgeResponse mapToDto(StoneRagKnowledgeResultEntity source) {
        StoneRagKnowledgeResponse target = new StoneRagKnowledgeResponse();
        if (source.getStone() != null) {
            StoneRagCandidateRequest reference = new StoneRagCandidateRequest(); reference.setId(source.getStone().getId()); reference.setName(source.getStone().getName());
            reference.setStoneType(source.getStone().getStoneType()); reference.setStoneSize(source.getStone().getStoneSize()); reference.setBiography(source.getStone().getBiography());
            target.setReference(reference);
        }
        target.setSources(source.getPassages().stream().map(passage -> {
            var fragment = passage.fragment();
            return new StoneRagSourceRequest(fragment.getDocumentKey() + "@" + fragment.getSourceVersion() + "#" + fragment.getFragmentOrder(),
                    fragment.getStoneType() == null ? StoneSemanticSupportSource.SHARED : StoneSemanticSupportSource.TYPE, fragment.getStoneType(), fragment.getText());
        }).toList()); return target;
    }
}
