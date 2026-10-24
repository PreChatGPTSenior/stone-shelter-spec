package lab.stoneshelter.entities;

import lab.stoneshelter.criteria.StoneExactSearchCriteria;

public record StoneExactSearchPreparationEntity(StoneExactSearchCriteria criteria, StoneChatTurnEntity earlyTurn) {}
