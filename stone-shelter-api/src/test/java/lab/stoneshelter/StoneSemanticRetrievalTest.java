package lab.stoneshelter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lab.stoneshelter.criteria.StoneExactSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticPassageCriteria;
import lab.stoneshelter.criteria.StoneSemanticRetrievalCriteria;
import lab.stoneshelter.criteria.StoneSemanticSearchCriteria;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticSearchResultEntity;
import lab.stoneshelter.entities.StoneSemanticSearchStateEntity;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSemanticSearchMode;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneSemanticRetrievalTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static StoneSemanticSearchRepository repository;
    private final List<Long> ids = new ArrayList<>();
    private final String documentKey = StoneDocumentIndexEntity.DOCUMENT_KEY;
    private static final String SOURCE_VERSION = "a".repeat(64);

    @BeforeAll
    static void startIsolatedDatabase() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        springLiquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(Transactions.class, StoneSemanticSearchRepository.class);
        context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        repository = context.getBean(StoneSemanticSearchRepository.class);
    }

    @AfterEach
    void deleteOnlyOwnedData() {
        jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key=?", documentKey);
        jdbcTemplate.update("DELETE FROM stone_document_index WHERE document_key=?", documentKey);
        for (long id : ids) {
            jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id=?", id);
            jdbcTemplate.update("DELETE FROM stone WHERE id=?", id);
        }
    }

    @AfterAll
    static void closeInfrastructure() { if (context != null) context.close(); DATABASE.stop(); }

    @Test
    void exactAndAvailablePredicatesPrecedeTheCandidateCapIncludingUtcBoundaries() {
        for (int index = 0; index < 8; index++) {
            card("wrong-type", StoneType.GRANITE, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-06T12:00:00Z", 1.0);
            card("wrong-size", StoneType.BASALT, StoneSize.LARGE, AdoptionStatus.AVAILABLE, "2026-10-06T12:00:00Z", 1.0);
            card("reserved", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.RESERVED, "2026-10-06T12:00:00Z", 1.0);
        }
        card("before", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-05T23:59:59Z", 1.0);
        card("end-exclusive", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-07T00:00:00Z", 1.0);
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < 8; index++) expected.add(card("eligible", StoneType.BASALT, StoneSize.SMALL,
                AdoptionStatus.AVAILABLE, index == 0 ? "2026-10-06T00:00:00Z" : "2026-10-06T12:00:00Z", 1.0));
        StoneExactSearchCriteria stoneExactSearchCriteria = exact();
        stoneExactSearchCriteria.setAdmissionDateRanges(List.of(new StoneExactSearchCriteria.DateRange(
                Instant.parse("2026-10-06T00:00:00Z"), Instant.parse("2026-10-07T00:00:00Z"))));
        var result = repository.search(retrieval(stoneExactSearchCriteria, List.of()));
        assertThat(result.candidates()).extracting(candidate -> candidate.getStone().getId()).containsExactlyElementsOf(expected.subList(0, 6));
        assertThat(result.candidates()).allSatisfy(candidate -> {
            assertThat(candidate.getCardScore()).isEqualTo(1.0);
            assertThat(candidate.getStone().getBiography()).isEqualTo("Current factual biography");
        });
    }

    @Test
    void applicableDocumentRecallDoesNotRequireAQualifyingOrExistingCardVector() {
        document(0, StoneType.GRANITE, "Granite shelf source", 1.0);
        document(1, StoneType.BASALT, "Basalt shelf source", 1.0);
        document(2, StoneType.BASALT, "Below threshold", 0.0);
        long missingVector = card("missing-vector", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE,
                "2026-10-06T12:00:00Z", null);
        long belowCardThreshold = card("below-card", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE,
                "2026-10-06T12:00:00Z", 0.0);
        var passages = repository.findPassages(new StoneSemanticPassageCriteria(query(), List.of(StoneType.BASALT),
                new StoneSemanticPassageCriteria.Bounds(1, 1.0)));
        assertThat(passages).hasSize(1);
        assertThat(passages.getFirst().fragment().getFragmentOrder()).isEqualTo(1);
        assertThat(passages.getFirst().fragment().getSourceVersion()).isEqualTo(SOURCE_VERSION);
        assertThat(passages.getFirst().score()).isEqualTo(1.0);
        var result = repository.search(retrieval(exact(), passages));
        assertThat(result.candidates()).extracting(candidate -> candidate.getStone().getId())
                .containsExactly(missingVector, belowCardThreshold);
        assertThat(result.candidates().getFirst().getCardScore()).isNull();
        assertThat(result.candidates().getLast().getCardScore()).isZero();
        assertThat(result.candidates()).allSatisfy(candidate -> assertThat(candidate.getPassages()).hasSize(1));
    }

    @Test
    void alternativesUseNewestThenIdAndFinalEligibilityUsesLiveFacts() {
        long old = card("old", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-01T12:00:00Z", null);
        long firstTie = card("tie-1", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-06T12:00:00Z", null);
        long secondTie = card("tie-2", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-06T12:00:00Z", null);
        var alternatives = repository.searchAlternatives(retrieval(exact(), List.of()));
        assertThat(alternatives).extracting(candidate -> candidate.getStone().getId()).containsExactly(firstTie, secondTie, old);
        jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", firstTie);
        jdbcTemplate.update("UPDATE stone SET size='LARGE' WHERE id=?", secondTie);
        assertThat(repository.findEligible(List.of(firstTie, secondTie, old), exact())).extracting(stone -> stone.getId()).containsExactly(old);
        StoneExactSearchCriteria unfiltered = new StoneExactSearchCriteria();
        assertThatThrownBy(() -> repository.searchAlternatives(retrieval(unfiltered, List.of()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incompatiblePublishedProfilesFailInsteadOfLookingLikeNoMatches() {
        long id = card("profile", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-06T12:00:00Z", 1.0);
        var bare = new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL, StoneDocumentEmbeddingInputEntity.PROFILE, vector(1), 1);
        assertThatThrownBy(() -> repository.findPassages(new StoneSemanticPassageCriteria(bare, List.of(), new StoneSemanticPassageCriteria.Bounds(10, 0.5))))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        jdbcTemplate.update("UPDATE stone_card_embedding SET embedding_profile=? WHERE stone_id=?", StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "b".repeat(64), id);
        assertThatThrownBy(() -> repository.search(retrieval(exact(), List.of()))).isInstanceOf(StoneDocumentDependencyUnavailableException.class);
    }

    @Test
    void settingsRejectInvalidSuppliedValuesAndMapperRetainsInternalFacts() {
        var defaults = settings(new MockEnvironment());
        assertThat(defaults.candidateLimit()).isEqualTo(10);
        assertThat(defaults.documentLimit()).isEqualTo(10);
        assertThat(defaults.cardMinimumSimilarity()).isEqualTo(0.5);
        assertThat(defaults.documentMinimumSimilarity()).isEqualTo(0.5);
        assertThat(defaults.maximumEvidenceCalls()).isEqualTo(20);
        for (String name : List.of("STONE_SEMANTIC_SEARCH_CANDIDATE_LIMIT", "STONE_SEMANTIC_SEARCH_DOCUMENT_LIMIT", "STONE_SEMANTIC_SEARCH_MAX_EVIDENCE_CALLS")) {
            assertThatThrownBy(() -> settings(new MockEnvironment().withProperty(name, "0"))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> settings(new MockEnvironment().withProperty("STONE_SEMANTIC_SEARCH_CANDIDATE_LIMIT", "5"))).isInstanceOf(IllegalArgumentException.class);
        for (String value : List.of("NaN", "Infinity", "1.1", "-1.1", "bad")) {
            assertThatThrownBy(() -> settings(new MockEnvironment().withProperty("STONE_SEMANTIC_SEARCH_DOCUMENT_MIN_SIMILARITY", value)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        long id = card("mapped", StoneType.BASALT, StoneSize.SMALL, AdoptionStatus.AVAILABLE, "2026-10-06T12:00:00Z", 1.0);
        StoneSemanticCandidateEntity candidate = repository.search(retrieval(exact(), List.of())).candidates().getFirst();
        candidate.setMatchedWishes(List.of(new StoneSemanticWish("use", "shelf")));
        candidate.setUnmatchedWishes(List.of(new StoneSemanticWish("color", "blue")));
        candidate.setCardSupported(true);
        StoneSemanticSearchStateEntity state = new StoneSemanticSearchStateEntity(); state.setExact(exact()); state.setWishes(candidate.getMatchedWishes());
        StoneSemanticSearchResultEntity result = new StoneSemanticSearchResultEntity();
        result.setMode(StoneSemanticSearchMode.SEMANTIC); result.setFoundCount(1); result.setText("One candidate");
        result.setCandidates(List.of(candidate)); result.setState(state);
        var mapper = new StoneSemanticSearchResultEntityToStoneSemanticSearchResponseMapper();
        var response = mapper.toDto(result);
        assertThat(response.getCandidates().getFirst().getId()).isEqualTo(id);
        assertThat(response.getCandidates().getFirst().getBiography()).isEqualTo("Current factual biography");
        assertThat(response.getCandidates().getFirst().getUnmatchedWishes().getFirst().text()).isEqualTo("blue");
        assertThat(response.getState().wishes().getFirst().key()).isEqualTo("use");
        assertThatThrownBy(() -> mapper.toDto(null)).isInstanceOf(MapperValidationException.class);
    }

    private StoneSemanticSearchSettings settings(MockEnvironment environment) {
        return new StoneSemanticSearchSettings(environment, new StoneCardSearchSettings(environment));
    }

    private long card(String name, StoneType type, StoneSize size, AdoptionStatus status, String admissionDate, Double score) {
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO stone(storage_uuid,name,stone_type,size,adoption_status,admission_date,biography)
                VALUES(?,?,?,?,?,?,?) RETURNING id
                """, Long.class, UUID.randomUUID(), name, type.name(), size.name(), status.name(),
                Timestamp.from(Instant.parse(admissionDate)), "Current factual biography");
        ids.add(id);
        if (score != null) jdbcTemplate.update("INSERT INTO stone_card_embedding(stone_id,text,embedding_profile,embedding) VALUES(?,?,?,?::vector)",
                id, "Stale projection must not become factual authority", (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), Arrays.toString(vector(score)));
        return id;
    }

    private void document(int order, StoneType type, String text, double score) {
        jdbcTemplate.update("""
                INSERT INTO stone_document_index(document_key,source_version,embedding_profile,language,indexed_fragments)
                VALUES(?,?,?,'en',3) ON CONFLICT DO NOTHING
                """, documentKey, SOURCE_VERSION, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)));
        jdbcTemplate.update("""
                INSERT INTO stone_document_fragment(document_key,fragment_order,source_version,embedding_profile,language,stone_type,section_path,passage_text,embedding)
                VALUES(?,?,?,?,'en',?,'test',?,?::vector)
                """, documentKey, order, SOURCE_VERSION, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), type.name(), text, Arrays.toString(vector(score)));
    }

    private StoneExactSearchCriteria exact() {
        StoneExactSearchCriteria stoneExactSearchCriteria = new StoneExactSearchCriteria();
        stoneExactSearchCriteria.setStoneTypes(List.of(StoneType.BASALT)); stoneExactSearchCriteria.setStoneSizes(List.of(StoneSize.SMALL));
        stoneExactSearchCriteria.setAvailability(List.of(AdoptionStatus.AVAILABLE));
        return stoneExactSearchCriteria;
    }

    private StoneSemanticRetrievalCriteria retrieval(StoneExactSearchCriteria exact, List<lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity.Passage> passages) {
        return new StoneSemanticRetrievalCriteria(new StoneSemanticSearchCriteria(exact, List.of(new StoneSemanticWish("use", "shelf"))),
                query(), passages, new StoneSemanticRetrievalCriteria.Bounds(6, 1.0));
    }

    private StoneDocumentEmbeddingEntity query() {
        return new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), vector(1), 1);
    }

    private double[] vector(double score) {
        double[] vector = new double[1024]; vector[0] = score; vector[1] = Math.sqrt(1 - score * score); return vector;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
