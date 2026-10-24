package lab.stoneshelter.mappers.dtos;

import java.util.List;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.shared.StoneSemanticEvidenceCandidateRequest;
import lab.stoneshelter.shared.StoneSemanticEvidenceSourceRequest;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper
        extends AbstractDtoMapper<StoneSemanticCandidateEntity, StoneSemanticEvidenceCandidateRequest> {
    @Override
    protected StoneSemanticEvidenceCandidateRequest mapToDto(StoneSemanticCandidateEntity stoneSemanticCandidateEntity) {
        var stoneEntity = stoneSemanticCandidateEntity.getStone();
        String card = "Name: " + stoneEntity.getName() + ". Type: " + stoneEntity.getStoneType().name()
                + ". Size: " + stoneEntity.getStoneSize().name() + "."
                + (stoneEntity.getBiography() == null ? "" : " Biography: " + stoneEntity.getBiography());
        return new StoneSemanticEvidenceCandidateRequest(stoneEntity.getId(), stoneEntity.getStoneType(), card,
                stoneSemanticCandidateEntity.getPassages().stream().map(passage -> {
                    var fragment = passage.fragment();
                    return new StoneSemanticEvidenceSourceRequest(fragment.getDocumentKey() + "@" + fragment.getSourceVersion()
                            + "#" + fragment.getFragmentOrder(), fragment.getStoneType() == null
                            ? StoneSemanticSupportSource.SHARED : StoneSemanticSupportSource.TYPE, fragment.getStoneType(), fragment.getText());
                }).toList());
    }
    public List<StoneSemanticWishRequest> toDto(List<StoneSemanticWish> wishes) {
        return wishes.stream().map(wish -> new StoneSemanticWishRequest(wish.key(), wish.text())).toList();
    }
}
