package lab.stoneshelter.mappers.entities;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneRagAnswerAssemblyEntity;
import lab.stoneshelter.entities.StoneRagGeneratedAnswerEntity;
import lab.stoneshelter.entities.StoneSemanticCheckedCandidateEntity;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneSemanticSearchMode;
import org.springframework.stereotype.Component;

@Component
public class StoneRagAnswerAssemblyEntityToStoneChatTurnEntityMapper extends AbstractEntityMapper<StoneRagAnswerAssemblyEntity, StoneChatTurnEntity> {
    @Override protected StoneChatTurnEntity newEntity() { return new StoneChatTurnEntity(); }
    @Override protected StoneChatTurnEntity populateEntity(StoneRagAnswerAssemblyEntity source, StoneChatTurnEntity target) {
        target.setTurnId(source.original().getTurnId()); target.setMessage(source.original().getMessage()); target.setContext(source.original().getContext());
        target.setExactSearchCriteria(source.selection().getState().getExact()); target.setSemanticSearchState(source.selection().getState()); target.setRagState(source.route());
        target.setStones(source.selection().getCandidates().stream().limit(5).map(candidate -> new StoneChatStoneEntity(candidate.getStone().getId(), candidate.getStone().getName())).toList());
        if (source.route().mode() == StoneRagMode.SELECTION) target.setSemanticCandidates(source.selection().getCandidates().stream().limit(5)
                .map(candidate -> new StoneSemanticCheckedCandidateEntity(candidate.getStone().getId(), candidate.getMatchedWishes(), candidate.getUnmatchedWishes(), candidate.getEvidence())).toList());
        if (source.answer() == null) target.setText(source.selection().getText());
        else if (source.route().mode() == StoneRagMode.KNOWLEDGE) target.setText(source.answer().knowledge());
        else {
            Map<Long, String> explanations = source.answer().stones().stream().collect(Collectors.toMap(StoneRagGeneratedAnswerEntity.Explanation::id, StoneRagGeneratedAnswerEntity.Explanation::explanation));
            String introduction = source.selection().getMode() == StoneSemanticSearchMode.EXACT_FALLBACK
                    ? "Suitable semantic candidates were not found. Here are exact alternatives; semantic wishes remain unconfirmed."
                    : source.selection().getState().getWishes().isEmpty() ? "Found " + source.selection().getFoundCount() + " matching available stones."
                    : "Found " + source.selection().getFoundCount() + " suitable candidates in the checked set.";
            StringBuilder text = new StringBuilder(introduction);
            source.selection().getCandidates().stream().limit(5).forEach(candidate -> text.append("\n").append(candidate.getStone().getName()).append(": ").append(explanations.get(candidate.getStone().getId())));
            if (source.selection().getHasMore()) text.append("\nMore candidates were found."); target.setText(text.toString());
        }
        return target;
    }
}
