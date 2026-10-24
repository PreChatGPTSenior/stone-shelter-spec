package lab.stoneshelter;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneExactSearchPersistenceTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff").asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static lab.stoneshelter.repositories.StoneExactSearchRepository repository;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 12);
    @BeforeAll static void start() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase(); liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(Transactions.class, lab.stoneshelter.repositories.StoneExactSearchRepository.class); context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class); repository = context.getBean(lab.stoneshelter.repositories.StoneExactSearchRepository.class);
    }
    @AfterEach void clean() { jdbcTemplate.update("DELETE FROM stone"); }
    @AfterAll static void stop() { if (context != null) context.close(); DATABASE.stop(); }

    @Test void groupsUseOrWithinAndAcrossAndVisibilityIsMandatory() {
        insert("basalt-small", "BASALT", "SMALL", "AVAILABLE", "2026-10-01T00:00:00Z");
        insert("granite-small", "GRANITE", "SMALL", "AVAILABLE", "2026-10-01T00:00:00Z");
        insert("basalt-large", "BASALT", "LARGE", "AVAILABLE", "2026-10-01T00:00:00Z");
        insert("reserved", "BASALT", "SMALL", "RESERVED", "2026-10-01T00:00:00Z");
        var criteria = new lab.stoneshelter.criteria.StoneExactSearchCriteria();
        criteria.setStoneTypes(List.of(lab.stoneshelter.enums.StoneType.BASALT, lab.stoneshelter.enums.StoneType.GRANITE));
        criteria.setStoneSizes(List.of(lab.stoneshelter.enums.StoneSize.SMALL));
        assertThat(repository.search(criteria).getTotal()).isEqualTo(2);
        criteria.setAvailability(List.of(lab.stoneshelter.enums.AdoptionStatus.RESERVED));
        assertThat(repository.search(criteria).getTotal()).isZero();
    }
    @Test void multipleRangesExcludeTheirEndsAndCountAllWithFiveOrderedReferences() {
        for (int index = 0; index < 7; index++) insert("same-" + index, "BASALT", "SMALL", "AVAILABLE", "2026-10-01T00:00:00Z");
        insert("end", "BASALT", "SMALL", "AVAILABLE", "2026-10-02T00:00:00Z");
        insert("newest", "BASALT", "SMALL", "AVAILABLE", "2026-10-03T00:00:00Z");
        var criteria = new lab.stoneshelter.criteria.StoneExactSearchCriteria();
        criteria.setAdmissionDateRanges(List.of(new lab.stoneshelter.criteria.StoneExactSearchCriteria.DateRange(
                java.time.Instant.parse("2026-10-01T00:00:00Z"), java.time.Instant.parse("2026-10-02T00:00:00Z")),
                new lab.stoneshelter.criteria.StoneExactSearchCriteria.DateRange(java.time.Instant.parse("2026-10-03T00:00:00Z"), java.time.Instant.parse("2026-10-04T00:00:00Z"))));
        var result = repository.search(criteria); assertThat(result.getTotal()).isEqualTo(8);
        assertThat(result.getStones()).hasSize(5); assertThat(result.getStones().getFirst().getName()).isEqualTo("newest");
        assertThat(result.getStones().subList(1,5).stream().map(lab.stoneshelter.entities.StoneEntity::getId).toList()).isSorted();
    }
    @Test void referenceLookupUsesCurrentAvailabilityAndUnfilteredQueriesAreRejected() {
        long id = insert("shown", "BASALT", "SMALL", "AVAILABLE", "2026-10-01T00:00:00Z");
        assertThat(repository.findById(id)).isPresent(); jdbcTemplate.update("UPDATE stone SET adoption_status='ADOPTED' WHERE id=?", id);
        assertThat(repository.findById(id)).isEmpty();
        assertThatThrownBy(() -> repository.search(new lab.stoneshelter.criteria.StoneExactSearchCriteria())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void legacyAndOptionalCriteriaHistoryAreBackwardReadable() {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var turn = json.readValue("{\"turnId\":\""+UUID.randomUUID()+"\",\"message\":\"basalt\",\"text\":\"old\",\"stones\":[]}", lab.stoneshelter.entities.StoneChatTurnEntity.class);
        assertThat(turn.getExactSearchCriteria()).isNull();
        var criteria = new lab.stoneshelter.criteria.StoneExactSearchCriteria(); criteria.setStoneTypes(List.of(lab.stoneshelter.enums.StoneType.BASALT));
        turn.setExactSearchCriteria(criteria);
        assertThat(json.readValue(json.writeValueAsString(turn), lab.stoneshelter.entities.StoneChatTurnEntity.class).getExactSearchCriteria().getStoneTypes())
                .containsExactly(lab.stoneshelter.enums.StoneType.BASALT);
    }
    private long insert(String name, String type, String size, String status, String date) {
        return jdbcTemplate.queryForObject("INSERT INTO stone(storage_uuid,name,stone_type,size,adoption_status,admission_date) VALUES (?,?,?,?,?,?) RETURNING id",
                Long.class, UUID.randomUUID(), name, type, size, status, java.sql.Timestamp.from(java.time.Instant.parse(date)));
    }
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
