package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;
import lab.stoneshelter.entities.StoneSemanticSearchResultEntity;
import lab.stoneshelter.shared.StoneSemanticCandidateResponse;
import lab.stoneshelter.shared.StoneSemanticSearchResponse;
import lab.stoneshelter.shared.StoneSemanticSearchStateResponse;
import lab.stoneshelter.shared.StoneSemanticSourceResponse;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import lab.stoneshelter.shared.StoneSemanticWishResponse;
import lab.stoneshelter.shared.StoneSemanticWishEvidenceResponse;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper
        extends AbstractDtoMapper<StoneSemanticSearchResultEntity, StoneSemanticSearchResponse> {
    @Override
    protected StoneSemanticSearchResponse mapToDto(StoneSemanticSearchResultEntity stoneSemanticSearchResultEntity) {
        StoneSemanticSearchResponse stoneSemanticSearchResponse = new StoneSemanticSearchResponse();
        stoneSemanticSearchResponse.setMode(stoneSemanticSearchResultEntity.getMode());
        stoneSemanticSearchResponse.setFoundCount(stoneSemanticSearchResultEntity.getFoundCount());
        stoneSemanticSearchResponse.setHasMore(stoneSemanticSearchResultEntity.getHasMore());
        stoneSemanticSearchResponse.setText(stoneSemanticSearchResultEntity.getText());
        stoneSemanticSearchResponse.setCandidates(stoneSemanticSearchResultEntity.getCandidates().stream().map(this::toDto).toList());
        if (stoneSemanticSearchResultEntity.getState() != null) {
            var state = stoneSemanticSearchResultEntity.getState();
            stoneSemanticSearchResponse.setState(new StoneSemanticSearchStateResponse(state.getExact(), state.getWishes().stream()
                    .map(wish -> new StoneSemanticWishRequest(wish.key(), wish.text())).toList(), state.getMode(), state.getActualSelection()));
        }
        return stoneSemanticSearchResponse;
    }

    private StoneSemanticCandidateResponse toDto(StoneSemanticCandidateEntity stoneSemanticCandidateEntity) {
        StoneSemanticCandidateResponse stoneSemanticCandidateResponse = new StoneSemanticCandidateResponse();
        stoneSemanticCandidateResponse.setId(stoneSemanticCandidateEntity.getStone().getId());
        stoneSemanticCandidateResponse.setName(stoneSemanticCandidateEntity.getStone().getName());
        stoneSemanticCandidateResponse.setStoneType(stoneSemanticCandidateEntity.getStone().getStoneType());
        stoneSemanticCandidateResponse.setStoneSize(stoneSemanticCandidateEntity.getStone().getStoneSize());
        stoneSemanticCandidateResponse.setBiography(stoneSemanticCandidateEntity.getStone().getBiography());
        stoneSemanticCandidateResponse.setCardScore(stoneSemanticCandidateEntity.getCardScore());
        stoneSemanticCandidateResponse.setCardSupported(stoneSemanticCandidateEntity.getCardSupported());
        stoneSemanticCandidateResponse.setSupportingDocumentScore(stoneSemanticCandidateEntity.getSupportingDocumentScore());
        stoneSemanticCandidateResponse.setMatchedWishes(stoneSemanticCandidateEntity.getMatchedWishes().stream()
                .map(wish -> new StoneSemanticWishResponse(wish.key(), wish.text())).toList());
        stoneSemanticCandidateResponse.setUnmatchedWishes(stoneSemanticCandidateEntity.getUnmatchedWishes().stream()
                .map(wish -> new StoneSemanticWishResponse(wish.key(), wish.text())).toList());
        stoneSemanticCandidateResponse.setSources(stoneSemanticCandidateEntity.getPassages().stream().map(this::toDto).toList());
        stoneSemanticCandidateResponse.setEvidence(stoneSemanticCandidateEntity.getEvidence().stream()
                .map(judgement -> new StoneSemanticWishEvidenceResponse(judgement.key(), judgement.sourceId(), judgement.verdict(), judgement.quote())).toList());
        return stoneSemanticCandidateResponse;
    }

    private StoneSemanticSourceResponse toDto(StoneSemanticRetrievalResultEntity.Passage passage) {
        StoneSemanticSourceResponse stoneSemanticSourceResponse = new StoneSemanticSourceResponse();
        var fragment = passage.fragment();
        stoneSemanticSourceResponse.setId(fragment.getDocumentKey() + "@" + fragment.getSourceVersion() + "#" + fragment.getFragmentOrder());
        stoneSemanticSourceResponse.setText(fragment.getText());
        stoneSemanticSourceResponse.setStoneType(fragment.getStoneType());
        stoneSemanticSourceResponse.setScore(passage.score());
        stoneSemanticSourceResponse.setProvenance(new StoneSemanticSourceResponse.Provenance(fragment.getDocumentKey(),
                fragment.getSourceVersion(), fragment.getSectionPath(), fragment.getFragmentOrder()));
        return stoneSemanticSourceResponse;
    }
}
