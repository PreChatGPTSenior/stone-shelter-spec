package lab.stoneshelter.entities;

public record StoneRagAnswerAssemblyEntity(StoneChatTurnEntity original, StoneSemanticSearchResultEntity selection,
        StoneRagGeneratedAnswerEntity answer, StoneRagTurnStateEntity route) {}
