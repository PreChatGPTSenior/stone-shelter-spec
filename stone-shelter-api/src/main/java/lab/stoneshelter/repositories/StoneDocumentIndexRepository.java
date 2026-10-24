package lab.stoneshelter.repositories;

import java.sql.ResultSet;
import lab.stoneshelter.criteria.StoneDocumentSearchCriteria;
import lab.stoneshelter.entities.StoneDocumentSearchResultEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneDocumentIndexOperationEntity;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;
import lab.stoneshelter.enums.StoneType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoneDocumentIndexRepository {
    private final JdbcTemplate jdbcTemplate;

    public StoneDocumentIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<StoneDocumentIndexEntity> findById(String documentKey) {
        return jdbcTemplate.query("SELECT * FROM stone_document_index WHERE document_key = ?",
                this::stoneDocumentIndexEntity, documentKey).stream().findFirst();
    }

    public StoneDocumentIndexEntity save(StoneDocumentIndexEntity stoneDocumentIndexEntity) {
        jdbcTemplate.update("""
                INSERT INTO stone_document_index
                    (document_key, source_version, embedding_profile, language, indexed_fragments, active_operation_key, owner_token)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (document_key) DO UPDATE SET source_version = EXCLUDED.source_version,
                    embedding_profile = EXCLUDED.embedding_profile, language = EXCLUDED.language,
                    indexed_fragments = EXCLUDED.indexed_fragments,
                    active_operation_key = EXCLUDED.active_operation_key, owner_token = EXCLUDED.owner_token
                """, stoneDocumentIndexEntity.getDocumentKey(), stoneDocumentIndexEntity.getSourceVersion(),
                stoneDocumentIndexEntity.getEmbeddingProfile(), stoneDocumentIndexEntity.getLanguage(),
                stoneDocumentIndexEntity.getIndexedFragments(), stoneDocumentIndexEntity.getActiveOperationKey(),
                stoneDocumentIndexEntity.getOwnerToken());
        return stoneDocumentIndexEntity;
    }

    public Optional<StoneDocumentIndexOperationEntity> findOperation(UUID idempotencyKey) {
        return jdbcTemplate.query("SELECT * FROM stone_document_index_operation WHERE idempotency_key = ?",
                this::operation, idempotencyKey).stream().findFirst();
    }

    public StoneDocumentIndexOperationEntity insertOperation(StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity) {
        jdbcTemplate.update("""
                INSERT INTO stone_document_index_operation
                    (idempotency_key, document_key, kind, state, owner_token, deadline, started_at,
                     committed_at, replay_until, source_version, indexed_fragments, outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, stoneDocumentIndexOperationEntity.getIdempotencyKey(), stoneDocumentIndexOperationEntity.getDocumentKey(),
                stoneDocumentIndexOperationEntity.getKind().name(), stoneDocumentIndexOperationEntity.getState().name(),
                stoneDocumentIndexOperationEntity.getOwnerToken(), timestamp(stoneDocumentIndexOperationEntity.getDeadline()),
                timestamp(stoneDocumentIndexOperationEntity.getStartedAt()), timestamp(stoneDocumentIndexOperationEntity.getCommittedAt()),
                timestamp(stoneDocumentIndexOperationEntity.getReplayUntil()), stoneDocumentIndexOperationEntity.getSourceVersion(),
                stoneDocumentIndexOperationEntity.getIndexedFragments(),
                stoneDocumentIndexOperationEntity.getOutcome() == null ? null : stoneDocumentIndexOperationEntity.getOutcome().name());
        return stoneDocumentIndexOperationEntity;
    }

    public List<StoneDocumentFragmentEntity> findFragments(String documentKey) {
        return jdbcTemplate.query("SELECT * FROM stone_document_fragment WHERE document_key = ? ORDER BY fragment_order",
                this::fragment, documentKey);
    }

    public List<StoneDocumentFragmentEntity> insertFragments(List<StoneDocumentFragmentEntity> stoneDocumentFragmentEntities)
            throws IllegalArgumentException {
        List<Object[]> parameters = new ArrayList<>();
        for (StoneDocumentFragmentEntity stoneDocumentFragmentEntity : stoneDocumentFragmentEntities) {
            parameters.add(new Object[]{stoneDocumentFragmentEntity.getDocumentKey(), stoneDocumentFragmentEntity.getFragmentOrder(),
                    stoneDocumentFragmentEntity.getSourceVersion(), stoneDocumentFragmentEntity.getEmbeddingProfile(),
                    stoneDocumentFragmentEntity.getLanguage(), stoneDocumentFragmentEntity.getStoneType() == null
                            ? null : stoneDocumentFragmentEntity.getStoneType().name(),
                    stoneDocumentFragmentEntity.getSectionPath(), stoneDocumentFragmentEntity.getText(),
                    vector(stoneDocumentFragmentEntity.getEmbedding())});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO stone_document_fragment
                    (document_key, fragment_order, source_version, embedding_profile, language, stone_type,
                     section_path, passage_text, embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                """, parameters);
        return stoneDocumentFragmentEntities;
    }

    public void deleteFragments(String documentKey) {
        jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key = ?", documentKey);
    }

    public StoneDocumentSearchResultEntity search(StoneDocumentSearchCriteria criteria) {
        if (criteria.limit() < 1 || criteria.limit() > 5) throw new IllegalArgumentException("The search limit must be one to five.");
        StoneDocumentIndexEntity stoneDocumentIndexEntity = findById(StoneDocumentIndexEntity.DOCUMENT_KEY).orElse(null);
        if (stoneDocumentIndexEntity != null && stoneDocumentIndexEntity.getEmbeddingProfile() != null && !stoneDocumentIndexEntity.getEmbeddingProfile().equals(criteria.profile())) {
            throw new StoneDocumentDependencyUnavailableException("The query embedding profile differs from the published index.");
        }
        return new StoneDocumentSearchResultEntity(jdbcTemplate.query("""
                SELECT f.*, 1 - (f.embedding <=> ?::vector) AS score
                FROM stone_document_fragment f
                WHERE f.document_key = ? AND f.embedding_profile = ?
                ORDER BY f.embedding <=> ?::vector, f.fragment_order
                LIMIT ?
                """, (resultSet, row) -> new StoneDocumentSearchResultEntity.Passage(fragment(resultSet, row), resultSet.getDouble("score")),
                vector(criteria.vector()), StoneDocumentIndexEntity.DOCUMENT_KEY, criteria.profile(), vector(criteria.vector()), criteria.limit()));
    }

    public Optional<StoneDocumentIndexOperationEntity> findOwnedOperation(UUID key, UUID owner) {
        return jdbcTemplate.query("""
                SELECT o.* FROM stone_document_index_operation o
                JOIN stone_document_index i ON i.document_key = o.document_key
                WHERE o.idempotency_key = ? AND o.owner_token = ? AND o.state = 'RUNNING'
                    AND i.active_operation_key = o.idempotency_key AND i.owner_token = o.owner_token
                """, this::operation, key, owner).stream().findFirst();
    }

    public boolean tryMutationLock() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(17, 1)", Boolean.class));
    }

    public void ensureAnchor() {
        jdbcTemplate.update("INSERT INTO stone_document_index(document_key) VALUES (?) ON CONFLICT DO NOTHING",
                StoneDocumentIndexEntity.DOCUMENT_KEY);
    }

    public boolean claimOwner(StoneDocumentIndexOperationEntity operation) {
        return jdbcTemplate.update("UPDATE stone_document_index SET active_operation_key = ?, owner_token = ? "
                + "WHERE document_key = ? AND active_operation_key IS NULL",
                operation.getIdempotencyKey(), operation.getOwnerToken(), operation.getDocumentKey()) == 1;
    }

    public void updateOperation(StoneDocumentIndexOperationEntity operation) {
        jdbcTemplate.update("""
                UPDATE stone_document_index_operation SET state = ?, owner_token = ?, deadline = ?,
                    committed_at = ?, replay_until = ?, source_version = ?, indexed_fragments = ?, outcome = ?
                WHERE idempotency_key = ?
                """, operation.getState().name(), operation.getOwnerToken(), timestamp(operation.getDeadline()),
                timestamp(operation.getCommittedAt()), timestamp(operation.getReplayUntil()), operation.getSourceVersion(),
                operation.getIndexedFragments(), operation.getOutcome() == null ? null : operation.getOutcome().name(),
                operation.getIdempotencyKey());
    }

    public void clearOwner(UUID key, UUID ownerToken) {
        jdbcTemplate.update("UPDATE stone_document_index SET active_operation_key = NULL, owner_token = NULL "
                + "WHERE active_operation_key = ? AND owner_token = ?", key, ownerToken);
    }

    private StoneDocumentIndexEntity stoneDocumentIndexEntity(ResultSet resultSet, int row) throws SQLException {
        StoneDocumentIndexEntity stoneDocumentIndexEntity = new StoneDocumentIndexEntity();
        stoneDocumentIndexEntity.setDocumentKey(resultSet.getString("document_key"));
        stoneDocumentIndexEntity.setSourceVersion(resultSet.getString("source_version"));
        stoneDocumentIndexEntity.setEmbeddingProfile(resultSet.getString("embedding_profile"));
        stoneDocumentIndexEntity.setLanguage(resultSet.getString("language"));
        stoneDocumentIndexEntity.setIndexedFragments(resultSet.getInt("indexed_fragments"));
        stoneDocumentIndexEntity.setActiveOperationKey(resultSet.getObject("active_operation_key", UUID.class));
        stoneDocumentIndexEntity.setOwnerToken(resultSet.getObject("owner_token", UUID.class));
        return stoneDocumentIndexEntity;
    }

    private StoneDocumentIndexOperationEntity operation(ResultSet resultSet, int row) throws SQLException {
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity = new StoneDocumentIndexOperationEntity();
        stoneDocumentIndexOperationEntity.setIdempotencyKey(resultSet.getObject("idempotency_key", UUID.class));
        stoneDocumentIndexOperationEntity.setDocumentKey(resultSet.getString("document_key"));
        stoneDocumentIndexOperationEntity.setKind(StoneDocumentIndexOperationKind.valueOf(resultSet.getString("kind")));
        stoneDocumentIndexOperationEntity.setState(StoneDocumentIndexOperationState.valueOf(resultSet.getString("state")));
        stoneDocumentIndexOperationEntity.setOwnerToken(resultSet.getObject("owner_token", UUID.class));
        stoneDocumentIndexOperationEntity.setDeadline(instant(resultSet.getTimestamp("deadline")));
        stoneDocumentIndexOperationEntity.setStartedAt(instant(resultSet.getTimestamp("started_at")));
        stoneDocumentIndexOperationEntity.setCommittedAt(instant(resultSet.getTimestamp("committed_at")));
        stoneDocumentIndexOperationEntity.setReplayUntil(instant(resultSet.getTimestamp("replay_until")));
        stoneDocumentIndexOperationEntity.setSourceVersion(resultSet.getString("source_version"));
        stoneDocumentIndexOperationEntity.setIndexedFragments(resultSet.getObject("indexed_fragments", Integer.class));
        stoneDocumentIndexOperationEntity.setOutcome(resultSet.getString("outcome") == null
                ? null : StoneDocumentIndexOutcome.valueOf(resultSet.getString("outcome")));
        return stoneDocumentIndexOperationEntity;
    }

    private StoneDocumentFragmentEntity fragment(ResultSet resultSet, int row) throws SQLException {
        StoneDocumentFragmentEntity stoneDocumentFragmentEntity = new StoneDocumentFragmentEntity();
        stoneDocumentFragmentEntity.setDocumentKey(resultSet.getString("document_key"));
        stoneDocumentFragmentEntity.setSourceVersion(resultSet.getString("source_version"));
        stoneDocumentFragmentEntity.setEmbeddingProfile(resultSet.getString("embedding_profile"));
        stoneDocumentFragmentEntity.setLanguage(resultSet.getString("language"));
        stoneDocumentFragmentEntity.setStoneType(resultSet.getString("stone_type") == null
                ? null : StoneType.valueOf(resultSet.getString("stone_type")));
        stoneDocumentFragmentEntity.setSectionPath(resultSet.getString("section_path"));
        stoneDocumentFragmentEntity.setFragmentOrder(resultSet.getInt("fragment_order"));
        stoneDocumentFragmentEntity.setText(resultSet.getString("passage_text"));
        stoneDocumentFragmentEntity.setEmbedding(Arrays.stream(resultSet.getString("embedding")
                .replace("[", "").replace("]", "").split(",")).mapToDouble(Double::parseDouble).toArray());
        return stoneDocumentFragmentEntity;
    }

    private String vector(double[] embedding) {
        if (embedding == null || embedding.length != StoneDocumentEmbeddingInputEntity.DIMENSIONS
                || Arrays.stream(embedding).anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("A finite 1024-dimensional embedding is required.");
        }
        return Arrays.toString(embedding);
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
