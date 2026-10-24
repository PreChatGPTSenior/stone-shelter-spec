package lab.stoneshelter;

import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.InvalidStoneCardRequestException;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.mappers.dtos.StoneCardEmbeddingEntityToStoneCardSaveResponseMapper;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneCardMappingTest {
    @Test
    void factualProjectionPreservesBiographyAndMapperNullContracts() {
        StoneCardSaveRequest stoneCardSaveRequest = new StoneCardSaveRequest();
        stoneCardSaveRequest.setStoneId(7L);
        stoneCardSaveRequest.setName("Buddy");
        stoneCardSaveRequest.setStoneType(StoneType.BASALT);
        stoneCardSaveRequest.setStoneSize(StoneSize.SMALL);
        StoneCardSaveRequestToStoneCardEmbeddingEntityMapper mapper = new StoneCardSaveRequestToStoneCardEmbeddingEntityMapper();
        for (String biography : new String[] { null, "", "  Quiet.\n  " }) {
            stoneCardSaveRequest.setBiography(biography);
            assertThat(mapper.toEntity(stoneCardSaveRequest).getText()).isEqualTo(
                    "Name: Buddy\nStone type: BASALT\nSize: SMALL\nBiography: " + (biography == null ? "" : biography));
        }
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(new StoneCardEmbeddingEntityToStoneCardSaveResponseMapper().toDto(mapper.toEntity(stoneCardSaveRequest)).stoneId()).isEqualTo(7);
        assertThatThrownBy(() -> new StoneCardEmbeddingEntityToStoneCardSaveResponseMapper().toDto(null))
                .isInstanceOf(MapperValidationException.class);
    }

    @Test
    void thresholdDefaultOverrideAndInvalidValues() {
        assertThat(new StoneCardSearchSettings(new MockEnvironment()).minimumSimilarity()).isEqualTo(0.5);
        for (String value : new String[] { "-1", "0.7", "1" }) {
            assertThat(new StoneCardSearchSettings(new MockEnvironment().withProperty("STONE_CARD_SEARCH_MIN_SIMILARITY", value))
                    .minimumSimilarity()).isEqualTo(Double.parseDouble(value));
        }
        for (String value : new String[] { "", "bad", "NaN", "Infinity", "-1.1", "1.1" }) {
            assertThatThrownBy(() -> new StoneCardSearchSettings(new MockEnvironment().withProperty("STONE_CARD_SEARCH_MIN_SIMILARITY", value))
                    .minimumSimilarity()).isInstanceOf(InvalidStoneCardRequestException.class);
        }
    }
}
