package lab.stoneshelter.mappers.dtos;

import lab.stoneshelter.criteria.StoneRagAnswerCriteria;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.shared.StoneRagCandidateRequest;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import lab.stoneshelter.shared.StoneRagSourceRequest;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StoneRagAnswerCriteriaToStoneRagGenerationRequestMapper extends AbstractDtoMapper<StoneRagAnswerCriteria, StoneRagGenerationRequest> {
    @Override protected StoneRagGenerationRequest mapToDto(StoneRagAnswerCriteria source) {
        StoneRagGenerationRequest target = new StoneRagGenerationRequest(); target.setMode(source.getMode()); target.setQuestion(source.getQuestion());
        target.setCandidates(source.getCandidates().stream().limit(5).map(this::toDto).toList());
        if (source.getReference() != null) target.setReference(toDto(source.getReference()));
        Map<String, StoneRagSourceRequest> sources = new LinkedHashMap<>();
        source.getPassages().forEach(passage -> { var mapped = toDto(passage); sources.put(mapped.id(), mapped); });
        source.getCandidates().stream().limit(5).forEach(candidate -> candidate.getPassages().forEach(passage -> {
            var mapped = toDto(passage); sources.putIfAbsent(mapped.id(), mapped);
        }));
        target.setSources(java.util.List.copyOf(sources.values())); target.setSelectionMode(source.getSelectionMode()); target.setDeadline(source.getDeadline()); return target;
    }
    private StoneRagCandidateRequest toDto(StoneSemanticCandidateEntity source) {
        StoneRagCandidateRequest target = toDto(source.getStone());
        target.setMatched(source.getMatchedWishes().stream().map(wish -> new StoneSemanticWishRequest(wish.key(), wish.text())).toList());
        target.setUnmatched(source.getUnmatchedWishes().stream().map(wish -> new StoneSemanticWishRequest(wish.key(), wish.text())).toList()); return target;
    }
    private StoneRagCandidateRequest toDto(StoneEntity source) {
        StoneRagCandidateRequest target = new StoneRagCandidateRequest(); target.setId(source.getId()); target.setName(source.getName());
        target.setStoneType(source.getStoneType()); target.setStoneSize(source.getStoneSize()); target.setBiography(source.getBiography()); return target;
    }
    private StoneRagSourceRequest toDto(StoneSemanticRetrievalResultEntity.Passage passage) {
        var fragment = passage.fragment();
        return new StoneRagSourceRequest(fragment.getDocumentKey() + "@" + fragment.getSourceVersion() + "#" + fragment.getFragmentOrder(),
                fragment.getStoneType() == null ? StoneSemanticSupportSource.SHARED : StoneSemanticSupportSource.TYPE, fragment.getStoneType(), fragment.getText());
    }
}
