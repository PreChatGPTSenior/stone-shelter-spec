package lab.stoneshelter.services;

import java.time.Instant;
import java.util.List;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;

public interface StoneExactSearchProvider {
    StoneModelResponseEntity invoke(StoneChatTurnEntity turn, List<StoneChatTurnEntity> history,
            Instant deadline, String repairReason);
    void probe();
}
