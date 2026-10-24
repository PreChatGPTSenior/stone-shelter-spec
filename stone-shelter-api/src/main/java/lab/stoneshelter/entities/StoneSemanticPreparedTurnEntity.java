package lab.stoneshelter.entities;

import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;

public record StoneSemanticPreparedTurnEntity(StoneChatTurnEntity turn, StoneSemanticSearchCriteria criteria,
        StoneSemanticSearchResultEntity selection, StoneSemanticSearchExecutionEntity execution) {}
