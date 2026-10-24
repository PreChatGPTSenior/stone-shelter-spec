package lab.stoneshelter;

import java.util.concurrent.CompletableFuture;
import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StoneCardEmbeddingPersistenceTest extends IntegrationTest {
    @Autowired private StoneCardEmbeddingRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id = 18001"); }

    @Test
    void replacesCompleteProjectionAndConfirmedRollbackPreservesPreviousRow() {
        repository.save(entity("first", "profile-a", 1));
        StoneCardEmbeddingEntity stoneCardEmbeddingEntity = repository.save(entity("second", "profile-b", 2));
        assertThat(stoneCardEmbeddingEntity.getText()).isEqualTo("second");
        assertThat(stoneCardEmbeddingEntity.getEmbeddingProfile()).isEqualTo("profile-b");
        assertThat(stoneCardEmbeddingEntity.getEmbedding()[0]).isEqualTo(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            repository.save(entity("rolled back", "profile-c", 3));
            status.setRollbackOnly();
        });
        assertThat(jdbcTemplate.queryForMap("SELECT text, embedding_profile, embedding::text AS vector FROM stone_card_embedding WHERE stone_id = 18001"))
                .containsEntry("text", "second").containsEntry("embedding_profile", "profile-b");
        assertThat(jdbcTemplate.queryForObject("SELECT embedding::text FROM stone_card_embedding WHERE stone_id = 18001", String.class)).startsWith("[2,0,");
    }

    @Test
    void concurrentSavesKeepOneCompleteIndependentProjection() {
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> repository.save(entity("first", "profile-a", 1)));
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> repository.save(entity("second", "profile-b", 2)));
        CompletableFuture.allOf(first, second).join();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding WHERE stone_id = 18001", Integer.class)).isEqualTo(1);
        String text = jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 18001", String.class);
        assertThat(jdbcTemplate.queryForObject("SELECT embedding_profile FROM stone_card_embedding WHERE stone_id = 18001", String.class))
                .isEqualTo(text.equals("first") ? "profile-a" : "profile-b");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone WHERE id = 18001", Integer.class)).isZero();
    }

    private StoneCardEmbeddingEntity entity(String text, String profile, double first) {
        StoneCardEmbeddingEntity stoneCardEmbeddingEntity = new StoneCardEmbeddingEntity();
        stoneCardEmbeddingEntity.setStoneId(18001L);
        stoneCardEmbeddingEntity.setText(text);
        stoneCardEmbeddingEntity.setEmbeddingProfile(profile);
        double[] vector = new double[1024]; vector[0] = first;
        stoneCardEmbeddingEntity.setEmbedding(vector);
        return stoneCardEmbeddingEntity;
    }
}
