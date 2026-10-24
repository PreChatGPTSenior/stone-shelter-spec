package lab.stoneshelter.entities;

import java.util.List;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;

public class StoneExactSearchResultEntity {
    private StoneChatTurnEntity turn;
    private StoneExactSearchCriteria criteria;
    private List<StoneEntity> stones = List.of();
    private long total;
    private String text;
    public StoneExactSearchResultEntity() {}
    public StoneChatTurnEntity getTurn() { return turn; }
    public void setTurn(StoneChatTurnEntity value) { turn = value; }
    public StoneExactSearchCriteria getCriteria() { return criteria; }
    public void setCriteria(StoneExactSearchCriteria value) { criteria = value; }
    public List<StoneEntity> getStones() { return stones; }
    public void setStones(List<StoneEntity> value) { stones = List.copyOf(value); }
    public long getTotal() { return total; }
    public void setTotal(long value) { total = value; }
    public String getText() { return text; }
    public void setText(String value) { text = value; }
}
