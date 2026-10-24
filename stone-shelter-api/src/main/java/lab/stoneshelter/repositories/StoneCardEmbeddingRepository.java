package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.entities.StoneCardSearchResultEntity;
import lab.stoneshelter.criteria.StoneCardSearchCriteria;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneCardEmbeddingRepository {
    private final JdbcTemplate jdbcTemplate;

    public StoneCardEmbeddingRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public StoneCardEmbeddingEntity save(StoneCardEmbeddingEntity stoneCardEmbeddingEntity) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO stone_card_embedding (stone_id, text, embedding_profile, embedding)
                VALUES (?, ?, ?, ?::vector)
                ON CONFLICT (stone_id) DO UPDATE SET text = EXCLUDED.text,
                    embedding_profile = EXCLUDED.embedding_profile, embedding = EXCLUDED.embedding
                RETURNING stone_id, text, embedding_profile, embedding::text AS vector_text
                """, this::mapEntity, stoneCardEmbeddingEntity.getStoneId(), stoneCardEmbeddingEntity.getText(),
                stoneCardEmbeddingEntity.getEmbeddingProfile(), vector(stoneCardEmbeddingEntity.getEmbedding()));
    }

    public List<Long> findOrphanIds() {
        return jdbcTemplate.query("""
                SELECT e.stone_id FROM stone_card_embedding e LEFT JOIN stone s ON s.id = e.stone_id
                WHERE s.id IS NULL ORDER BY e.stone_id
                """, (resultSet, row) -> resultSet.getLong(1));
    }

    public void deleteById(long stoneId) {
        jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id = ?", stoneId);
    }

    public boolean hasIncompatibleProfile(String embeddingProfile) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM stone_card_embedding WHERE embedding_profile <> ?)", Boolean.class, embeddingProfile));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public StoneCardSearchResultEntity search(StoneCardSearchCriteria stoneCardSearchCriteria) {
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM stone_card_embedding WHERE embedding_profile <> ?)",
                Boolean.class, stoneCardSearchCriteria.profile()))) {
            throw new StoneDocumentDependencyUnavailableException("The query embedding profile differs from the stone-card index.");
        }
        return new StoneCardSearchResultEntity(jdbcTemplate.query("""
                SELECT stone_id, 1 - (embedding <=> ?::vector) AS score
                FROM stone_card_embedding
                WHERE embedding_profile = ? AND 1 - (embedding <=> ?::vector) >= ?
                ORDER BY score DESC, stone_id ASC
                LIMIT ?
                """, (resultSet, row) -> new StoneCardSearchResultEntity.Match(resultSet.getLong("stone_id"), resultSet.getDouble("score")),
                vector(stoneCardSearchCriteria.vector()), stoneCardSearchCriteria.profile(),
                vector(stoneCardSearchCriteria.vector()), stoneCardSearchCriteria.minimumSimilarity(), stoneCardSearchCriteria.limit()));
    }

    private StoneCardEmbeddingEntity mapEntity(ResultSet resultSet, int row) throws SQLException {
        StoneCardEmbeddingEntity stoneCardEmbeddingEntity = new StoneCardEmbeddingEntity();
        stoneCardEmbeddingEntity.setStoneId(resultSet.getLong("stone_id"));
        stoneCardEmbeddingEntity.setText(resultSet.getString("text"));
        stoneCardEmbeddingEntity.setEmbeddingProfile(resultSet.getString("embedding_profile"));
        String vectorText = resultSet.getString("vector_text");
        stoneCardEmbeddingEntity.setEmbedding(Arrays.stream(vectorText.substring(1, vectorText.length() - 1).split(","))
                .mapToDouble(Double::parseDouble).toArray());
        return stoneCardEmbeddingEntity;
    }

    private String vector(double[] embedding) {
        return Arrays.stream(embedding).mapToObj(Double::toString).collect(Collectors.joining(",", "[", "]"));
    }
}
