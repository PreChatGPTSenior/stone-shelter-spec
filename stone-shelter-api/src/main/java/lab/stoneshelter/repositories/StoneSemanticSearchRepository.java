package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticPassageCriteria;
import lab.stoneshelter.criteria.StoneSemanticRetrievalCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true, timeout = 5)
public class StoneSemanticSearchRepository {
    private final JdbcTemplate jdbcTemplate;

    public StoneSemanticSearchRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public List<StoneSemanticRetrievalResultEntity.Passage> findPassages(StoneSemanticPassageCriteria stoneSemanticPassageCriteria) {
        requireProfile(stoneSemanticPassageCriteria.query());
        String vector = vector(stoneSemanticPassageCriteria.query());
        List<Object> parameters = new ArrayList<>(List.of(vector, stoneSemanticPassageCriteria.query().profile(), vector,
                stoneSemanticPassageCriteria.bounds().minimumSimilarity(), StoneDocumentIndexEntity.DOCUMENT_KEY));
        StringBuilder sql = new StringBuilder("""
                SELECT f.document_key,f.source_version,f.embedding_profile,f.language,f.stone_type,
                    f.section_path,f.fragment_order,f.passage_text,1-(f.embedding <=> ?::vector) AS score
                FROM stone_document_fragment f JOIN stone_document_index i ON i.document_key=f.document_key
                    AND i.source_version=f.source_version AND i.embedding_profile=f.embedding_profile
                WHERE f.embedding_profile=? AND 1-(f.embedding <=> ?::vector)>=? AND f.language='en' AND f.document_key=?
                """);
        if (!stoneSemanticPassageCriteria.types().isEmpty()) {
            sql.append(" AND (f.stone_type IS NULL OR f.stone_type IN (")
                    .append(placeholders(stoneSemanticPassageCriteria.types().size())).append("))");
            stoneSemanticPassageCriteria.types().forEach(type -> parameters.add(type.name()));
        }
        sql.append(" ORDER BY score DESC,f.document_key ASC,f.fragment_order ASC LIMIT ?");
        parameters.add(stoneSemanticPassageCriteria.bounds().limit());
        return jdbcTemplate.query(sql.toString(), this::passage, parameters.toArray());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public StoneSemanticRetrievalResultEntity search(StoneSemanticRetrievalCriteria stoneSemanticRetrievalCriteria) {
        if (stoneSemanticRetrievalCriteria.selection().wishes().isEmpty()) {
            throw new IllegalArgumentException("Semantic recall requires a meaningful wish");
        }
        requireProfile(stoneSemanticRetrievalCriteria.query());
        Map<StoneType, Double> typeScores = typeScores(stoneSemanticRetrievalCriteria.passages());
        List<Object> parameters = new ArrayList<>(List.of(vector(stoneSemanticRetrievalCriteria.query()),
                stoneSemanticRetrievalCriteria.query().profile()));
        StringBuilder sql = new StringBuilder("""
                WITH scored AS (
                  SELECT s.*,1-(e.embedding <=> ?::vector) AS card_score
                  FROM stone s LEFT JOIN stone_card_embedding e ON e.stone_id=s.id AND e.embedding_profile=?
                ) SELECT s.*,
                """);
        if (typeScores.isEmpty()) sql.append("NULL::double precision AS document_score");
        else {
            sql.append("CASE s.stone_type");
            for (var entry : typeScores.entrySet()) {
                sql.append(" WHEN ? THEN ?::double precision");
                parameters.add(entry.getKey().name()); parameters.add(entry.getValue());
            }
            sql.append(" END AS document_score");
        }
        sql.append(" FROM scored s WHERE s.adoption_status='AVAILABLE'");
        exact(sql, parameters, stoneSemanticRetrievalCriteria.selection().exact());
        sql.append(" AND (s.card_score>=?");
        parameters.add(stoneSemanticRetrievalCriteria.bounds().minimumCardSimilarity());
        if (!typeScores.isEmpty()) {
            sql.append(" OR s.stone_type IN (").append(placeholders(typeScores.size())).append(')');
            typeScores.keySet().forEach(type -> parameters.add(type.name()));
        }
        sql.append(") ORDER BY CASE WHEN s.card_score>=? THEN 0 ELSE 1 END,")
                .append(" CASE WHEN s.card_score>=? THEN s.card_score END DESC NULLS LAST, CASE WHEN s.card_score>=? THEN s.id END ASC NULLS LAST,document_score DESC NULLS LAST,s.id ASC LIMIT ?");
        parameters.add(stoneSemanticRetrievalCriteria.bounds().minimumCardSimilarity());
        parameters.add(stoneSemanticRetrievalCriteria.bounds().minimumCardSimilarity());
        parameters.add(stoneSemanticRetrievalCriteria.bounds().minimumCardSimilarity());
        parameters.add(stoneSemanticRetrievalCriteria.bounds().limit());
        return new StoneSemanticRetrievalResultEntity(jdbcTemplate.query(sql.toString(), (row, index) -> {
            StoneSemanticCandidateEntity stoneSemanticCandidateEntity = new StoneSemanticCandidateEntity();
            stoneSemanticCandidateEntity.setStone(stone(row, index));
            double cardScore = row.getDouble("card_score");
            stoneSemanticCandidateEntity.setCardScore(row.wasNull() ? null : cardScore);
            stoneSemanticCandidateEntity.setPassages(stoneSemanticRetrievalCriteria.passages().stream()
                    .filter(passage -> passage.fragment().getStoneType() == null
                            || passage.fragment().getStoneType() == stoneSemanticCandidateEntity.getStone().getStoneType()).toList());
            return stoneSemanticCandidateEntity;
        }, parameters.toArray()), stoneSemanticRetrievalCriteria.passages());
    }

    public List<StoneSemanticCandidateEntity> searchAlternatives(StoneSemanticRetrievalCriteria stoneSemanticRetrievalCriteria) {
        if (!stoneSemanticRetrievalCriteria.selection().exact().hasNarrowing()) {
            throw new IllegalArgumentException("Exact alternatives require real narrowing");
        }
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT s.* FROM stone s WHERE s.adoption_status='AVAILABLE'");
        exact(sql, parameters, stoneSemanticRetrievalCriteria.selection().exact());
        sql.append(" ORDER BY s.admission_date DESC,s.id ASC LIMIT ?");
        parameters.add(stoneSemanticRetrievalCriteria.bounds().limit());
        return jdbcTemplate.query(sql.toString(), (row, index) -> {
            StoneSemanticCandidateEntity stoneSemanticCandidateEntity = new StoneSemanticCandidateEntity();
            stoneSemanticCandidateEntity.setStone(stone(row, index));
            return stoneSemanticCandidateEntity;
        }, parameters.toArray());
    }

    public List<StoneEntity> findEligible(List<Long> ids, StoneExactSearchCriteria stoneExactSearchCriteria) {
        if (ids.isEmpty()) return List.of();
        List<Object> parameters = new ArrayList<>(ids);
        StringBuilder sql = new StringBuilder("SELECT s.* FROM stone s WHERE s.adoption_status='AVAILABLE' AND s.id IN (")
                .append(placeholders(ids.size())).append(')');
        exact(sql, parameters, stoneExactSearchCriteria);
        return jdbcTemplate.query(sql.toString(), this::stone, parameters.toArray());
    }

    private void requireProfile(StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity) {
        if (!StoneDocumentEmbeddingInputEntity.MODEL.equals(stoneDocumentEmbeddingEntity.model())
                || stoneDocumentEmbeddingEntity.profile() == null
                || !stoneDocumentEmbeddingEntity.profile().matches(Pattern.quote(StoneDocumentEmbeddingInputEntity.PROFILE) + "/[0-9a-f]{64}")) {
            throw new StoneDocumentDependencyUnavailableException("Incompatible semantic query profile");
        }
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM stone_card_embedding WHERE embedding_profile<>?)
                    OR EXISTS(SELECT 1 FROM stone_document_index WHERE document_key=? AND embedding_profile IS NOT NULL AND embedding_profile<>?)
                """, Boolean.class, stoneDocumentEmbeddingEntity.profile(), StoneDocumentIndexEntity.DOCUMENT_KEY, stoneDocumentEmbeddingEntity.profile()))) {
            throw new StoneDocumentDependencyUnavailableException("Published source profiles differ from the semantic query");
        }
    }

    private String vector(StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity) {
        double[] vector = stoneDocumentEmbeddingEntity.vector();
        if (vector.length != 1024 || Arrays.stream(vector).anyMatch(value -> !Double.isFinite(value))
                || Arrays.stream(vector).allMatch(value -> value == 0)) {
            throw new IllegalArgumentException("A finite nonzero 1024-dimensional query is required");
        }
        return Arrays.stream(vector).mapToObj(Double::toString).collect(Collectors.joining(",", "[", "]"));
    }

    private Map<StoneType, Double> typeScores(List<StoneSemanticRetrievalResultEntity.Passage> passages) {
        Map<StoneType, Double> scores = new EnumMap<>(StoneType.class);
        for (var passage : passages) {
            for (StoneType type : StoneType.values()) {
                if (passage.fragment().getStoneType() == null || passage.fragment().getStoneType() == type) {
                    scores.merge(type, passage.score(), Math::max);
                }
            }
        }
        return scores;
    }

    private void exact(StringBuilder sql, List<Object> parameters, StoneExactSearchCriteria stoneExactSearchCriteria) {
        values(sql, parameters, "s.stone_type", stoneExactSearchCriteria.getStoneTypes());
        values(sql, parameters, "s.size", stoneExactSearchCriteria.getStoneSizes());
        values(sql, parameters, "s.adoption_status", stoneExactSearchCriteria.getAvailability());
        if (stoneExactSearchCriteria.getStoneId() != null) { sql.append(" AND s.id=?"); parameters.add(stoneExactSearchCriteria.getStoneId()); }
        if (!stoneExactSearchCriteria.getAdmissionDateRanges().isEmpty()) {
            sql.append(" AND (");
            for (int index = 0; index < stoneExactSearchCriteria.getAdmissionDateRanges().size(); index++) {
                if (index > 0) sql.append(" OR ");
                sql.append("(s.admission_date>=? AND s.admission_date<?)");
                var range = stoneExactSearchCriteria.getAdmissionDateRanges().get(index);
                parameters.add(Timestamp.from(range.startInclusive())); parameters.add(Timestamp.from(range.endExclusive()));
            }
            sql.append(')');
        }
    }

    private void values(StringBuilder sql, List<Object> parameters, String column, List<? extends Enum<?>> values) {
        if (values.isEmpty()) return;
        sql.append(" AND ").append(column).append(" IN (").append(placeholders(values.size())).append(')');
        values.forEach(value -> parameters.add(value.name()));
    }

    private String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }

    private StoneEntity stone(ResultSet row, int index) throws SQLException {
        StoneEntity stoneEntity = new StoneEntity();
        stoneEntity.setId(row.getLong("id")); stoneEntity.setName(row.getString("name"));
        stoneEntity.setStorageUuid(row.getObject("storage_uuid", UUID.class));
        stoneEntity.setPhoto(row.getString("photo_url"));
        stoneEntity.setStoneType(StoneType.valueOf(row.getString("stone_type")));
        stoneEntity.setStoneSize(StoneSize.valueOf(row.getString("size")));
        stoneEntity.setAdoptionStatus(AdoptionStatus.valueOf(row.getString("adoption_status")));
        stoneEntity.setBiography(row.getString("biography"));
        stoneEntity.setAdmissionDate(row.getTimestamp("admission_date").toInstant());
        return stoneEntity;
    }

    private StoneSemanticRetrievalResultEntity.Passage passage(ResultSet row, int index) throws SQLException {
        StoneDocumentFragmentEntity stoneDocumentFragmentEntity = new StoneDocumentFragmentEntity();
        stoneDocumentFragmentEntity.setDocumentKey(row.getString("document_key"));
        stoneDocumentFragmentEntity.setSourceVersion(row.getString("source_version"));
        stoneDocumentFragmentEntity.setEmbeddingProfile(row.getString("embedding_profile"));
        stoneDocumentFragmentEntity.setLanguage(row.getString("language"));
        String type = row.getString("stone_type");
        stoneDocumentFragmentEntity.setStoneType(type == null ? null : StoneType.valueOf(type));
        stoneDocumentFragmentEntity.setSectionPath(row.getString("section_path"));
        stoneDocumentFragmentEntity.setFragmentOrder(row.getInt("fragment_order"));
        stoneDocumentFragmentEntity.setText(row.getString("passage_text"));
        return new StoneSemanticRetrievalResultEntity.Passage(stoneDocumentFragmentEntity, row.getDouble("score"));
    }
}
