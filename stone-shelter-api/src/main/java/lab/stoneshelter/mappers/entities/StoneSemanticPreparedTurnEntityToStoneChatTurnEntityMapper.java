package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneSemanticCheckedCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticPreparedTurnEntity;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticPreparedTurnEntityToStoneChatTurnEntityMapper extends AbstractEntityMapper<StoneSemanticPreparedTurnEntity, StoneChatTurnEntity> {
    @Override protected StoneChatTurnEntity newEntity() { return new StoneChatTurnEntity(); }
    @Override protected StoneChatTurnEntity populateEntity(StoneSemanticPreparedTurnEntity source, StoneChatTurnEntity target) {
        target.setTurnId(source.turn().getTurnId()); target.setMessage(source.turn().getMessage()); target.setContext(source.turn().getContext());
        target.setExactSearchCriteria(source.criteria().exact()); target.setSemanticSearchState(source.selection().getState());
        target.setText(source.selection().getText());
        target.setStones(source.selection().getCandidates().stream().limit(5)
                .map(candidate -> new StoneChatStoneEntity(candidate.getStone().getId(), candidate.getStone().getName())).toList());
        target.setSemanticCandidates(source.selection().getCandidates().stream().limit(5)
                .map(candidate -> new StoneSemanticCheckedCandidateEntity(candidate.getStone().getId(), candidate.getMatchedWishes(),
                        candidate.getUnmatchedWishes(), candidate.getEvidence())).toList());
        return target;
    }
}
