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

class ModelTokenBudgetTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(DockerImageName.parse(
            "pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff").asCompatibleSubstituteFor("postgres"));
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbcTemplate;
    private static ModelTokenBudgetTransactionService ledger;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 12);
    @BeforeAll static void start() throws Exception {
        DATABASE.start();
        DataSource dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase(); liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
        context.register(Transactions.class, ModelTokenBudgetRepository.class, ModelTokenBudgetTransactionService.class); context.refresh();
        jdbcTemplate = context.getBean(JdbcTemplate.class); ledger = context.getBean(ModelTokenBudgetTransactionService.class);
    }
    @AfterEach void clean() { jdbcTemplate.update("DELETE FROM model_token_reservation"); jdbcTemplate.update("DELETE FROM model_token_budget_day"); }
    @AfterAll static void stop() { if (context != null) context.close(); DATABASE.stop(); }
    @Test void settlesOnlyConfirmedSurplusOnceAndRejectsConflictingOrExcessUsage() {
        ModelTokenReservationEntity reservation = ledger.reserve(UUID.randomUUID(), DAY, 8192, 100000);
        assertThat(ledger.settle(reservation.id(), 123).actualTokens()).isEqualTo(123L);
        assertThat(ledger.settle(reservation.id(), 123).actualTokens()).isEqualTo(123L);
        assertThatThrownBy(() -> ledger.settle(reservation.id(), 124)).isInstanceOf(IllegalStateException.class);
        assertThat(charged(DAY)).isEqualTo(123);
        ModelTokenReservationEntity other = ledger.reserve(UUID.randomUUID(), DAY, 10, 100000);
        assertThatThrownBy(() -> ledger.settle(other.id(), 11)).isInstanceOf(IllegalStateException.class);
        assertThat(charged(DAY)).isEqualTo(133);
    }
    @Test void unknownUsageSurvivesRestartAndNextUtcDayIsIndependent() {
        ModelTokenReservationEntity unknown = ledger.reserve(UUID.randomUUID(), DAY, 100, 100);
        assertThatThrownBy(() -> ledger.reserve(UUID.randomUUID(), DAY, 1, 100)).isInstanceOf(ModelTokenBudgetExceededException.class);
        assertThat(ledger.reserve(UUID.randomUUID(), DAY.plusDays(1), 100, 100).budgetDay()).isEqualTo(DAY.plusDays(1));
        assertThat(new ModelTokenBudgetRepository(jdbcTemplate).findById(unknown.id()).actualTokens()).isNull();
        assertThat(charged(DAY)).isEqualTo(100); assertThat(charged(DAY.plusDays(1))).isEqualTo(100);
        assertThat(ledger.settle(unknown.id(), 10).budgetDay()).isEqualTo(DAY);
        assertThat(charged(DAY)).isEqualTo(10); assertThat(charged(DAY.plusDays(1))).isEqualTo(100);
    }
    @Test void concurrentReservationsCannotOverdrawBudget() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<Boolean> attempt = () -> { try { ledger.reserve(UUID.randomUUID(), DAY, 10, 30); return true; }
                catch (ModelTokenBudgetExceededException exception) { return false; } };
            int accepted = 0;
            for (var result : executor.invokeAll(java.util.Collections.nCopies(10, attempt))) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(3); assertThat(charged(DAY)).isEqualTo(30);
        }
    }
    @Test void rejectedReservationLeavesNoInvocationOrExtraCharge() {
        ledger.reserve(UUID.randomUUID(), DAY, 80, 100);
        assertThatThrownBy(() -> ledger.reserve(UUID.randomUUID(), DAY, 30, 100)).isInstanceOf(ModelTokenBudgetExceededException.class);
        assertThat(charged(DAY)).isEqualTo(80);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class)).isEqualTo(1);
        ledger.probe();
    }
    private long charged(LocalDate day) { return jdbcTemplate.queryForObject("SELECT charged_tokens FROM model_token_budget_day WHERE budget_day=?", Long.class, day); }
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }
}
