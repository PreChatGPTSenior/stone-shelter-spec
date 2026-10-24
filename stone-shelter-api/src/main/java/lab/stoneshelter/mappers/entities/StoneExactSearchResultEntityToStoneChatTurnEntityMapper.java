package lab.stoneshelter.mappers.entities;

import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneExactSearchResultEntity;
import org.springframework.stereotype.Component;

@Component
public class StoneExactSearchResultEntityToStoneChatTurnEntityMapper extends AbstractEntityMapper<StoneExactSearchResultEntity, StoneChatTurnEntity> {
    @Override protected StoneChatTurnEntity newEntity() { return new StoneChatTurnEntity(); }
    @Override protected StoneChatTurnEntity populateEntity(StoneExactSearchResultEntity source, StoneChatTurnEntity target) {
        target.setTurnId(source.getTurn().getTurnId()); target.setMessage(source.getTurn().getMessage()); target.setContext(source.getTurn().getContext());
        target.setText(source.getText()); target.setExactSearchCriteria(source.getCriteria());
        target.setStones(source.getStones().stream().map(stone -> new StoneChatStoneEntity(stone.getId(), stone.getName())).toList());
        return target;
    }
}
