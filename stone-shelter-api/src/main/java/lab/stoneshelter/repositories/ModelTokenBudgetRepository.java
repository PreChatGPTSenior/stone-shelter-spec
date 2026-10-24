package lab.stoneshelter.repositories;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.entities.ModelTokenBudgetDayEntity;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ModelTokenBudgetRepository {
    private final JdbcTemplate jdbcTemplate;
    public ModelTokenBudgetRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    public ModelTokenReservationEntity reserve(UUID id, LocalDate day, long tokens, long limit) {
        if (tokens <= 0 || limit <= 0) throw new IllegalArgumentException("Invalid token reservation");
        jdbcTemplate.update("INSERT INTO model_token_budget_day(budget_day, charged_tokens) VALUES (?,0) ON CONFLICT DO NOTHING", day);
        long charged = jdbcTemplate.queryForObject("SELECT charged_tokens FROM model_token_budget_day WHERE budget_day=? FOR UPDATE", Long.class, day);
        if (tokens > limit || charged > limit - tokens)
            throw new ModelTokenBudgetExceededException(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        jdbcTemplate.update("INSERT INTO model_token_reservation(id,budget_day,reserved_tokens) VALUES (?,?,?)", id, day, tokens);
        jdbcTemplate.update("UPDATE model_token_budget_day SET charged_tokens=charged_tokens+? WHERE budget_day=?", tokens, day);
        return new ModelTokenReservationEntity(id, day, tokens, null);
    }
    public ModelTokenReservationEntity settle(UUID id, long actual) {
        ModelTokenReservationEntity reservation = findById(id);
        if (actual < 0 || actual > reservation.reservedTokens()) throw new IllegalStateException("Usage exceeds qualified reservation");
        if (reservation.actualTokens() != null) {
            if (reservation.actualTokens() != actual) throw new IllegalStateException("Conflicting usage settlement");
            return reservation;
        }
        jdbcTemplate.update("UPDATE model_token_budget_day SET charged_tokens=charged_tokens-? WHERE budget_day=?",
                reservation.reservedTokens() - actual, reservation.budgetDay());
        jdbcTemplate.update("UPDATE model_token_reservation SET actual_tokens=? WHERE id=?", actual, id);
        return new ModelTokenReservationEntity(id, reservation.budgetDay(), reservation.reservedTokens(), actual);
    }
    public ModelTokenReservationEntity findById(UUID id) {
        return jdbcTemplate.queryForObject("SELECT id,budget_day,reserved_tokens,actual_tokens FROM model_token_reservation WHERE id=? FOR UPDATE",
                (row, index) -> new ModelTokenReservationEntity(row.getObject("id", UUID.class), row.getObject("budget_day", LocalDate.class),
                        row.getLong("reserved_tokens"), row.getObject("actual_tokens", Long.class)), id);
    }
    public ModelTokenBudgetDayEntity findByDay(LocalDate day) {
        return jdbcTemplate.queryForObject("SELECT budget_day,charged_tokens FROM model_token_budget_day WHERE budget_day=?",
                (row, index) -> new ModelTokenBudgetDayEntity(row.getObject("budget_day", LocalDate.class), row.getLong("charged_tokens")), day);
    }
    public void probe() {
        jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_budget_day", Long.class);
        jdbcTemplate.queryForObject("SELECT count(*) FROM model_token_reservation", Long.class);
    }
}
