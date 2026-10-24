package lab.stoneshelter;

import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.mappers.dtos.AbstractDtoMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lab.stoneshelter.criteria.StoneRagKnowledgeCriteria;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneChatContextEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneRagExecutionEntity;
import lab.stoneshelter.entities.StoneRagTurnStateEntity;
import lab.stoneshelter.enums.StoneExactSearchReferenceKind;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper;
import lab.stoneshelter.repositories.StoneRagKnowledgeRepository;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.services.StoneChatReferenceService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneExactSearchSettings;
import lab.stoneshelter.services.StoneRagKnowledgeService;
import lab.stoneshelter.services.StoneRagSettings;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import lab.stoneshelter.shared.StoneExactSearchReference;
import lab.stoneshelter.shared.StoneRagKnowledgeRequest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoneRagKnowledgeTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff").asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private final Clock clock = Clock.systemUTC();
    private final List<Long> ids = new ArrayList<>();
    private final StoneDocumentEmbeddingService embedding = mock(StoneDocumentEmbeddingService.class);
    private StoneRagKnowledgeService service;
    private StoneRagKnowledgeRepository cards;
    @BeforeAll static void database() throws Exception {
        DATABASE.start(); DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        var liquibase = new SpringLiquibase(); liquibase.setDataSource(dataSource); liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(StoneSemanticRetrievalTest.Transactions.class, StoneRagKnowledgeRepository.class, StoneSemanticSearchRepository.class); context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class);
    }
    @AfterAll static void close() { if (context != null) context.close(); DATABASE.stop(); }
    @AfterEach void clean() {
        jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbcTemplate.update("DELETE FROM stone_document_index WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        for (long id : ids) jdbcTemplate.update("DELETE FROM stone WHERE id=?", id);
    }
    @BeforeEach void prepare() {
        when(embedding.embed(any())).thenAnswer(call -> {
            StoneDocumentEmbeddingCriteria criteria = call.getArgument(0); assertThat(criteria.query()).isTrue();
            assertThat(criteria.admissionMode()).isEqualTo(ModelAdmissionMode.CUSTOMER);
            assertThat(criteria.deadline()).isBeforeOrEqualTo(clock.instant().plusSeconds(50)); return query();
        });
        var settings = new StoneSemanticSearchSettings(new MockEnvironment().withProperty("STONE_SEMANTIC_SEARCH_DOCUMENT_LIMIT", "1")
                .withProperty("STONE_SEMANTIC_SEARCH_DOCUMENT_MIN_SIMILARITY", "1"), new StoneCardSearchSettings(new MockEnvironment()));
        cards = context.getBean(StoneRagKnowledgeRepository.class);
        service = new StoneRagKnowledgeService(cards, context.getBean(StoneSemanticSearchRepository.class), embedding,
                new StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper(), settings, clock);
    }
    @Test void knowledgeUsesBoundedApplicablePassagesWithoutSelectingAnySpecimenAndReusesQuery() {
        document(0, StoneType.GRANITE, "Granite care."); document(1, StoneType.BASALT, "Basalt care.");
        var execution = execution(); var criteria = new StoneRagKnowledgeCriteria("care", List.of(StoneType.BASALT), null);
        var result = service.prepare(criteria, execution);
        assertThat(result.getStone()).isNull(); assertThat(result.getPassages()).hasSize(1);
        assertThat(result.getPassages().getFirst().fragment().getStoneType()).isEqualTo(StoneType.BASALT);
        assertThat(result.getPassages().getFirst().score()).isEqualTo(1);
        service.prepare(criteria, execution); verify(embedding).embed(any()); assertThat(execution.getQueryEmbeddings()).isEqualTo(1);
        var mapped = service.search(new StoneRagKnowledgeRequest(criteria, clock.instant().plusSeconds(120)));
        assertThat(mapped.getReference()).isNull(); assertThat(mapped.getSources()).hasSize(1);
        assertThat(mapped.getSources().getFirst().text()).isEqualTo("Basalt care.");
    }
    @Test void referencedKnowledgeUsesCurrentAvailableFactsAndMissingReferenceNeverEmbeds() {
        long id = card("Current card biography"); document(0, StoneType.BASALT, "Basalt care.");
        var criteria = new StoneRagKnowledgeCriteria("care", List.of(StoneType.GRANITE), id);
        var result = service.prepare(criteria, execution()); assertThat(result.getStone().getBiography()).isEqualTo("Current card biography");
        assertThat(result.getPassages().getFirst().fragment().getStoneType()).isEqualTo(StoneType.BASALT);
        jdbcTemplate.update("UPDATE stone SET biography='Changed current biography' WHERE id=?", id);
        assertThat(service.search(new StoneRagKnowledgeRequest(criteria, clock.instant().plusSeconds(120))).getReference().getBiography()).isEqualTo("Changed current biography");
        clearInvocations(embedding); jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", id);
        assertThat(service.prepare(criteria, execution()).getStone()).isNull(); verifyNoInteractions(embedding);
        assertThat(cards.findById(id)).isEmpty();
    }
    @Test void twoQueryLimitIsSharedWithSelectionAndProfileFailuresAreErrors() {
        var execution = execution(); execution.setQueryEmbeddings(1); execution.getQueryCache().put("selection", query());
        service.prepare(new StoneRagKnowledgeCriteria("care", List.of(), null), execution); assertThat(execution.getQueryEmbeddings()).isEqualTo(2);
        service.prepare(new StoneRagKnowledgeCriteria("care", List.of(), null), execution); verify(embedding).embed(any());
        assertThatThrownBy(() -> service.prepare(new StoneRagKnowledgeCriteria("geology", List.of(), null), execution)).isInstanceOf(StoneRagAnswerLimitExceededException.class);
        document(0, StoneType.BASALT, "Basalt care."); jdbcTemplate.update("UPDATE stone_document_index SET embedding_profile='wrong' WHERE document_key=?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        assertThatThrownBy(() -> service.prepare(new StoneRagKnowledgeCriteria("care", List.of(), null), execution)).isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        assertThat(execution.getQueryEmbeddings()).isEqualTo(2);
    }
    @Test void ordinalSkipsKnowledgeConvenienceLinksAndKeepsLegacySelectionIdentity() {
        var reference = new StoneChatReferenceService(); var selection = turn(10, 11);
        var knowledge = turn(99); knowledge.setRagState(new StoneRagTurnStateEntity(StoneRagMode.KNOWLEDGE, StoneRagCompletionKind.GENERATED, selection.getTurnId(), false));
        assertThat(reference.resolve(new StoneExactSearchReference(StoneExactSearchReferenceKind.ORDINAL, 2), turn(), List.of(selection, knowledge))).hasValue(11);
        assertThat(reference.selectionReferenceTurnId(List.of(selection, knowledge))).isEqualTo(selection.getTurnId());
        assertThat(reference.resolve(new StoneExactSearchReference(StoneExactSearchReferenceKind.ORDINAL, 3), turn(), List.of(selection, knowledge))).isEmpty();
        var page = turn(); page.setContext(new StoneChatContextEntity(42L));
        assertThat(reference.resolve(new StoneExactSearchReference(StoneExactSearchReferenceKind.PAGE_CONTEXT, null), page, List.of())).hasValue(42);
    }
    @Test void settingsCountersAndMapperContractsRemainExplicit() {
        var model = new StoneExactSearchSettings(new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://qualification.invalid"));
        var settings = new StoneRagSettings(new MockEnvironment(), model); assertThat(settings.outputTokens()).isEqualTo(1024); assertThat(settings.maximumGenerationCalls()).isEqualTo(4);
        assertThat(settings.finalizationReserve()).isEqualTo(java.time.Duration.ofSeconds(5));
        for (String value : List.of("0", "8192", "invalid")) assertThatThrownBy(() -> new StoneRagSettings(new MockEnvironment().withProperty("CHAT_RAG_OUTPUT_TOKENS", value), model)).isInstanceOf(IllegalArgumentException.class);
        for (String value : List.of("0", "5", "invalid")) assertThatThrownBy(() -> new StoneRagSettings(new MockEnvironment().withProperty("CHAT_RAG_MAX_GENERATION_CALLS", value), model)).isInstanceOf(IllegalArgumentException.class);
        var execution = execution(); execution.setEvidenceCalls(7); execution.consumeGenerationCall(1); assertThat(execution.getEvidenceCalls()).isEqualTo(7);
        assertThatThrownBy(() -> execution.consumeGenerationCall(1)).isInstanceOf(StoneRagAnswerLimitExceededException.class);
        assertThat(new StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper()).isInstanceOf(AbstractDtoMapper.class);
        assertThatThrownBy(() -> new StoneRagKnowledgeResultEntityToStoneRagKnowledgeResponseMapper().toDto(null)).isInstanceOf(MapperValidationException.class);
    }
    private StoneRagExecutionEntity execution() { var execution = new StoneRagExecutionEntity(); execution.setDeadline(clock.instant().plusSeconds(120)); execution.setModelDeadline(execution.getDeadline().minusSeconds(5)); return execution; }
    private StoneChatTurnEntity turn(long... ids) { var turn = new StoneChatTurnEntity(); turn.setTurnId(UUID.randomUUID()); turn.setMessage("request"); turn.setText("answer"); turn.setStones(Arrays.stream(ids).mapToObj(id -> new StoneChatStoneEntity(id, "Stone " + id)).toList()); return turn; }
    private long card(String biography) { long id = jdbcTemplate.queryForObject("INSERT INTO stone(storage_uuid,name,stone_type,size,adoption_status,admission_date,biography) VALUES(?,'Referenced','BASALT','SMALL','AVAILABLE',?,?) RETURNING id", Long.class, UUID.randomUUID(), Timestamp.from(Instant.now()), biography); ids.add(id); return id; }
    private void document(int order, StoneType type, String text) {
        jdbcTemplate.update("INSERT INTO stone_document_index(document_key,source_version,embedding_profile,language,indexed_fragments) VALUES(?,?,?,'en',2) ON CONFLICT DO NOTHING", StoneDocumentIndexEntity.DOCUMENT_KEY, "a".repeat(64), (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)));
        jdbcTemplate.update("INSERT INTO stone_document_fragment(document_key,fragment_order,source_version,embedding_profile,language,stone_type,section_path,passage_text,embedding) VALUES(?,?,?,?,'en',?,'test',?,?::vector)", StoneDocumentIndexEntity.DOCUMENT_KEY, order, "a".repeat(64), (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), type.name(), text, Arrays.toString(query().vector()));
    }
    private StoneDocumentEmbeddingEntity query() { double[] vector = new double[1024]; vector[0] = 1; return new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL, (StoneDocumentEmbeddingInputEntity.PROFILE + "/" + "a".repeat(64)), vector, 1); }
}
