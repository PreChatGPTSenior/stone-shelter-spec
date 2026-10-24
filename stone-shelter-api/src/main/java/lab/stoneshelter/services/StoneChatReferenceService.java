package lab.stoneshelter.services;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.enums.StoneExactSearchReferenceKind;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.shared.StoneExactSearchReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StoneChatReferenceService {
    public OptionalLong resolve(StoneExactSearchReference reference, StoneChatTurnEntity turn, List<StoneChatTurnEntity> history) {
        if (reference == null) return OptionalLong.empty();
        if (reference.kind() == StoneExactSearchReferenceKind.PAGE_CONTEXT)
            return turn.getContext() == null ? OptionalLong.empty() : OptionalLong.of(turn.getContext().stoneId());
        for (int index = history.size() - 1; index >= 0; index--) {
            StoneChatTurnEntity previous = history.get(index);
            if (!isSelection(previous)) continue;
            return reference.ordinal() <= previous.getStones().size() ? OptionalLong.of(previous.getStones().get(reference.ordinal() - 1).id()) : OptionalLong.empty();
        }
        return OptionalLong.empty();
    }
    public UUID selectionReferenceTurnId(List<StoneChatTurnEntity> history) {
        for (int index = history.size() - 1; index >= 0; index--) if (isSelection(history.get(index))) return history.get(index).getTurnId();
        return null;
    }
    private boolean isSelection(StoneChatTurnEntity turn) {
        return turn.getStones() != null && !turn.getStones().isEmpty() && (turn.getRagState() == null
                || turn.getRagState().mode() == StoneRagMode.SELECTION && turn.getRagState().actualSelection());
    }
}
