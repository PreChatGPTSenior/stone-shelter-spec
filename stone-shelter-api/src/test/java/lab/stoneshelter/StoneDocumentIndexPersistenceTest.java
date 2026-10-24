package lab.stoneshelter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneDocumentIndexOperationEntity;
import lab.stoneshelter.enums.StoneDocumentIndexOperationKind;
import lab.stoneshelter.enums.StoneDocumentIndexOperationState;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.repositories.StoneDocumentIndexRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StoneDocumentIndexPersistenceTest extends IntegrationTest {
    @Autowired private StoneDocumentIndexRepository stoneDocumentIndexRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final String documentKey = "persistence-" + UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    private static final String PROFILE = "qualified-test-profile";

    @AfterEach
    void removeOwnData() {
        jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key = ?", documentKey);
        jdbcTemplate.update("UPDATE stone_document_index SET active_operation_key = NULL, owner_token = NULL WHERE document_key = ?", documentKey);
        jdbcTemplate.update("DELETE FROM stone_document_index_operation WHERE document_key = ?", documentKey);
        jdbcTemplate.update("DELETE FROM stone_document_index WHERE document_key = ?", documentKey);
    }

    @Test
    void vectorAndProvenanceRoundTripInDeterministicOrder() {
        stoneDocumentIndexRepository.save(index(true));
        stoneDocumentIndexRepository.insertFragments(List.of(fragment(1), fragment(0)));
        List<StoneDocumentFragmentEntity> stoneDocumentFragmentEntities = stoneDocumentIndexRepository.findFragments(documentKey);
        assertThat(stoneDocumentFragmentEntities).extracting(StoneDocumentFragmentEntity::getFragmentOrder).containsExactly(0, 1);
        assertThat(stoneDocumentFragmentEntities.getFirst().getEmbedding()).hasSize(1024);
        assertThat(stoneDocumentFragmentEntities.getFirst().getEmbedding()[0]).isEqualTo(1.0);
        assertThat(stoneDocumentFragmentEntities.getFirst().getSectionPath()).isEqualTo("Stone Encyclopedia > Basalt > Fictional Care Ritual");
        assertThat(stoneDocumentFragmentEntities.getFirst().getText()).contains("Basalt > Care Ritual");
        assertThat(stoneDocumentFragmentEntities.getFirst().getStoneType()).isEqualTo(StoneType.BASALT);
        assertThat(stoneDocumentFragmentEntities.getFirst().getSourceVersion()).isEqualTo(HASH);
        assertThat(stoneDocumentIndexRepository.findById(documentKey).orElseThrow().getEmbeddingProfile()).isEqualTo(PROFILE);
    }

    @Test
    void fragmentOrderAndSourceVersionAreDatabaseConstraints() {
        stoneDocumentIndexRepository.save(index(true));
        stoneDocumentIndexRepository.insertFragments(List.of(fragment(0)));
        assertThatThrownBy(() -> stoneDocumentIndexRepository.insertFragments(List.of(fragment(0))))
                .isInstanceOf(DataIntegrityViolationException.class);
        StoneDocumentFragmentEntity stoneDocumentFragmentEntity = fragment(1);
        stoneDocumentFragmentEntity.setSourceVersion("b".repeat(64));
        assertThatThrownBy(() -> stoneDocumentIndexRepository.insertFragments(List.of(stoneDocumentFragmentEntity)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stoneDocumentIndexRepository.findFragments(documentKey)).hasSize(1);
    }

    @Test
    void usedKeysSurviveProjectionDeletionAndCannotBeReusedAcrossKinds() {
        stoneDocumentIndexRepository.save(index(true));
        stoneDocumentIndexRepository.insertFragments(List.of(fragment(0)));
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity = operation();
        stoneDocumentIndexRepository.insertOperation(stoneDocumentIndexOperationEntity);
        stoneDocumentIndexRepository.deleteFragments(documentKey);
        stoneDocumentIndexRepository.save(index(false));
        assertThat(stoneDocumentIndexRepository.findOperation(stoneDocumentIndexOperationEntity.getIdempotencyKey())
                .orElseThrow().getState()).isEqualTo(StoneDocumentIndexOperationState.FAILED);
        stoneDocumentIndexOperationEntity.setKind(StoneDocumentIndexOperationKind.DELETE);
        assertThatThrownBy(() -> stoneDocumentIndexRepository.insertOperation(stoneDocumentIndexOperationEntity))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(stoneDocumentIndexRepository.findFragments(documentKey)).isEmpty();
        assertThat(stoneDocumentIndexRepository.findById(documentKey)).isPresent();
    }

    @Test
    void invalidVectorsNeverReachPersistence() {
        stoneDocumentIndexRepository.save(index(true));
        StoneDocumentFragmentEntity stoneDocumentFragmentEntity = fragment(0);
        stoneDocumentFragmentEntity.setEmbedding(new double[1023]);
        assertThatThrownBy(() -> stoneDocumentIndexRepository.insertFragments(List.of(stoneDocumentFragmentEntity)))
                .isInstanceOf(IllegalArgumentException.class);
        double[] embedding = new double[1024];
        embedding[0] = Double.NaN;
        stoneDocumentFragmentEntity.setEmbedding(embedding);
        assertThatThrownBy(() -> stoneDocumentIndexRepository.insertFragments(List.of(stoneDocumentFragmentEntity)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stoneDocumentIndexRepository.findFragments(documentKey)).isEmpty();
    }

    @Test
    void projectionAndLedgerContainNoRawSourceArchiveColumns() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name IN ('stone_document_index', 'stone_document_fragment', 'stone_document_index_operation')
                """, String.class)).noneMatch(column -> column.contains("markdown") || column.contains("snapshot")
                || column.contains("archive") || column.contains("raw_source"));
    }

    private StoneDocumentIndexEntity index(boolean published) {
        StoneDocumentIndexEntity stoneDocumentIndexEntity = new StoneDocumentIndexEntity();
        stoneDocumentIndexEntity.setDocumentKey(documentKey);
        if (published) {
            stoneDocumentIndexEntity.setSourceVersion(HASH);
            stoneDocumentIndexEntity.setEmbeddingProfile(PROFILE);
            stoneDocumentIndexEntity.setLanguage("en");
            stoneDocumentIndexEntity.setIndexedFragments(2);
        }
        return stoneDocumentIndexEntity;
    }

    private StoneDocumentFragmentEntity fragment(int ordinal) {
        StoneDocumentFragmentEntity stoneDocumentFragmentEntity = new StoneDocumentFragmentEntity();
        stoneDocumentFragmentEntity.setDocumentKey(documentKey);
        stoneDocumentFragmentEntity.setSourceVersion(HASH);
        stoneDocumentFragmentEntity.setEmbeddingProfile(PROFILE);
        stoneDocumentFragmentEntity.setLanguage("en");
        stoneDocumentFragmentEntity.setStoneType(StoneType.BASALT);
        stoneDocumentFragmentEntity.setSectionPath("Stone Encyclopedia > Basalt > Fictional Care Ritual");
        stoneDocumentFragmentEntity.setFragmentOrder(ordinal);
        stoneDocumentFragmentEntity.setText("Stone Encyclopedia > Basalt > Care Ritual\nShared silence.");
        double[] embedding = new double[1024];
        embedding[0] = 1;
        stoneDocumentFragmentEntity.setEmbedding(embedding);
        return stoneDocumentFragmentEntity;
    }

    private StoneDocumentIndexOperationEntity operation() {
        StoneDocumentIndexOperationEntity stoneDocumentIndexOperationEntity = new StoneDocumentIndexOperationEntity();
        stoneDocumentIndexOperationEntity.setIdempotencyKey(UUID.randomUUID());
        stoneDocumentIndexOperationEntity.setDocumentKey(documentKey);
        stoneDocumentIndexOperationEntity.setKind(StoneDocumentIndexOperationKind.IMPORT);
        stoneDocumentIndexOperationEntity.setState(StoneDocumentIndexOperationState.FAILED);
        stoneDocumentIndexOperationEntity.setStartedAt(Instant.parse("2026-10-12T00:00:00Z"));
        return stoneDocumentIndexOperationEntity;
    }
}
