package lab.stoneshelter;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneIndexSourceVersionEntity;
import lab.stoneshelter.enums.StoneIndexEventType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.mappers.dtos.StoneEntityToStoneIndexUpdatePayloadMapper;
import lab.stoneshelter.repositories.StoneIndexSourceVersionRepository;
import lab.stoneshelter.shared.StoneIndexDeletePayload;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StoneIndexSourceVersionTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
            .asCompatibleSubstituteFor("postgres"));
    private static JdbcTemplate jdbcTemplate;
    private static StoneIndexSourceVersionRepository repository;
    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setup() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE stone (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("INSERT INTO stone VALUES (10)");
        SpringLiquibase springLiquibase = new SpringLiquibase();
        springLiquibase.setDataSource(dataSource);
        springLiquibase.setChangeLog("classpath:db/changelog/0021-create-stone-index-source-version.xml");
        springLiquibase.afterPropertiesSet();
        repository = new StoneIndexSourceVersionRepository(jdbcTemplate);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void cleanup() { jdbcTemplate.update("DELETE FROM stone_index_source_version WHERE stone_id <> 10"); }

    @AfterAll
    static void stop() { DATABASE.stop(); }

    @Test
    void existingCardsAreSeededWithoutCreatingEvents() {
        assertThat(repository.findById(10).orElseThrow().sourceVersion()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name = 'outbox_event'", Long.class)).isZero();
    }

    @Test
    void newVersionStartsAtOneSurvivesDeletionAndRollsBackWithCaller() {
        jdbcTemplate.update("INSERT INTO stone VALUES (20)");
        assertThat(transactionTemplate.execute(status -> repository.increment(new StoneIndexSourceVersionEntity(20, 0))).sourceVersion()).isEqualTo(1);
        jdbcTemplate.update("DELETE FROM stone WHERE id = 20");
        assertThat(repository.findById(20).orElseThrow().sourceVersion()).isEqualTo(1);
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(repository.increment(new StoneIndexSourceVersionEntity(20, 0)).sourceVersion()).isEqualTo(2);
            status.setRollbackOnly();
        });
        assertThat(repository.findById(20).orElseThrow().sourceVersion()).isEqualTo(1);
    }

    @Test
    void concurrentTransactionsAllocateDistinctMonotonicVersions() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<Long> increment = () -> transactionTemplate.execute(status -> repository.increment(new StoneIndexSourceVersionEntity(30, 0))).sourceVersion();
            var results = executor.invokeAll(List.of(increment, increment, increment, increment));
            assertThat(List.of(results.get(0).get(), results.get(1).get(), results.get(2).get(), results.get(3).get()))
                    .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        }
        assertThat(repository.findById(30).orElseThrow().sourceVersion()).isEqualTo(4);
    }

    @Test
    void payloadsMatchTheCanonicalContractWithoutPublicDeliveryFields() throws Exception {
        StoneEntity stoneEntity = new StoneEntity();
        stoneEntity.setId(40L);
        stoneEntity.setName("Luna");
        stoneEntity.setStoneType(StoneType.BASALT);
        stoneEntity.setStoneSize(StoneSize.SMALL);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        JsonNode update = jsonMapper.valueToTree(new StoneEntityToStoneIndexUpdatePayloadMapper().toDto(stoneEntity, 2));
        JsonNode contract = jsonMapper.readTree(Path.of("../specs/0021-stone-card-index-event-publication/asyncapi.json"));
        assertThat(update.propertyNames()).containsExactlyInAnyOrderElementsOf(
                contract.at("/components/schemas/StoneIndexUpdatePayload/required").valueStream().map(JsonNode::asString).toList());
        assertThat(update.get("biography").isNull()).isTrue();
        assertThat(update.get("sourceVersion").asLong()).isEqualTo(2);
        assertThat(jsonMapper.valueToTree(new StoneIndexDeletePayload(40, 3)).propertyNames()).containsExactlyInAnyOrder("stoneId", "sourceVersion");
        assertThat(StoneIndexEventType.SCHEMA_VERSION).isEqualTo(contract.at("/components/schemas/StoneCardIndexUpdatedEvent/allOf/1/properties/schemaVersion/const").asInt());
    }
}
