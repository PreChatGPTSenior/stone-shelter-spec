package lab.stoneshelter;

import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.services.StoneCardEmbeddingService;
import lab.stoneshelter.services.StoneCardRetrievalService;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import lab.stoneshelter.shared.StoneCardSearchRequest;
import lab.stoneshelter.shared.StoneCardSearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StoneCardEmbeddingBasicQueryTest extends IntegrationTest {
    @Autowired private StoneCardEmbeddingService stoneCardEmbeddingService;
    @Autowired private StoneCardRetrievalService stoneCardRetrievalService;
    @Autowired private StoneCardSearchSettings stoneCardSearchSettings;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id = 18001"); }

    @Test
    void basicQueryFindsTheSavedCardUsingRealModelAndInitialThreshold() {
        assertThat(stoneCardSearchSettings.minimumSimilarity()).isEqualTo(0.5);
        StoneCardSaveRequest stoneCardSaveRequest = new StoneCardSaveRequest();
        stoneCardSaveRequest.setStoneId(18001L);
        stoneCardSaveRequest.setName("Basalt Buddy");
        stoneCardSaveRequest.setStoneType(StoneType.BASALT);
        stoneCardSaveRequest.setStoneSize(StoneSize.SMALL);
        stoneCardSaveRequest.setBiography("Basalt Buddy is a small basalt stone.");
        assertThat(stoneCardEmbeddingService.save(stoneCardSaveRequest).stoneId()).isEqualTo(18001);
        StoneCardSearchResponse stoneCardSearchResponse = stoneCardRetrievalService.search(
                new StoneCardSearchRequest("Find the small basalt stone named Basalt Buddy.", 1));
        assertThat(stoneCardSearchResponse.matches()).hasSize(1);
        assertThat(stoneCardSearchResponse.matches().getFirst().stoneId()).isEqualTo(18001);
        assertThat(Double.isFinite(stoneCardSearchResponse.matches().getFirst().score())).isTrue();
        assertThat(stoneCardSearchResponse.matches().getFirst().score()).isGreaterThanOrEqualTo(0.5);
        System.out.println("Basic stone-card query score: " + stoneCardSearchResponse.matches().getFirst().score());
    }
}
