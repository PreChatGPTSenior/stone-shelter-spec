package lab.stoneshelter;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneChatConversationEntity;
import lab.stoneshelter.entities.StoneChatContextEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.mappers.dtos.StoneChatConversationEntityToStoneChatHistoryResponseMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatHistoryMapperTest {
    @Test void pairsHaveExactTextOriginalContextOrderedStonesAndMatchingIds() {
        StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage("  original  ");
        turn.setText("answer"); turn.setContext(new StoneChatContextEntity(7L));
        turn.setStones(List.of(new StoneChatStoneEntity(3, "second"), new StoneChatStoneEntity(1, "first")));
        var response = new StoneChatConversationEntityToStoneChatHistoryResponseMapper().toDto(new StoneChatConversationEntity(1, UUID.randomUUID(), List.of(turn)));
        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().getFirst().getText()).isEqualTo("  original  ");
        assertThat(response.messages().getFirst().getContext().stoneId()).isEqualTo(7L);
        assertThat(response.messages().getFirst().getStones()).isEmpty();
        assertThat(response.messages().getLast().getContext()).isNull();
        assertThat(response.messages().getLast().getStones()).extracting(stone -> stone.id()).containsExactly(3L, 1L);
        assertThat(response.messages()).extracting(message -> message.getTurnId()).containsExactly(turn.getTurnId(), turn.getTurnId());
        assertThatThrownBy(() -> new StoneChatConversationEntityToStoneChatHistoryResponseMapper().toDto(null)).isInstanceOf(MapperValidationException.class);
    }
}
