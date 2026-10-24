package lab.stoneshelter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatContextEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.services.StoneChatContextSelector;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StoneChatContextSelectorTest {
    @Test
    void latestTenWholePairsPreserveOrderWithoutMutatingHistory() {
        List<StoneChatTurnEntity> turns = new ArrayList<>();
        for (int i = 0; i < 12; i++) turns.add(turn("turn" + i));
        assertThat(new StoneChatContextSelector().select(turns)).containsExactlyElementsOf(turns.subList(2, 12));
        assertThat(turns).hasSize(12);
    }
    @Test
    void referencesAndContextCountAndOversizedNewestPairIsNotSplit() {
        StoneChatContextSelector selector = new StoneChatContextSelector();
        StoneChatTurnEntity turn = turn("");
        int overhead = selector.representation(turn).length();
        turn.setMessage("x".repeat(20000 - overhead));
        assertThat(selector.select(List.of(turn))).containsExactly(turn);
        turn.setMessage(turn.getMessage() + "x");
        assertThat(selector.select(List.of(turn("older"), turn))).isEmpty();
        assertThat(selector.representation(turn)).contains("stoneId", "3", "second", "first");
    }
    private static StoneChatTurnEntity turn(String message) {
        StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage(message);
        turn.setContext(new StoneChatContextEntity(7L)); turn.setText("answer");
        turn.setStones(List.of(new StoneChatStoneEntity(3, "second"), new StoneChatStoneEntity(1, "first"))); return turn;
    }
}
