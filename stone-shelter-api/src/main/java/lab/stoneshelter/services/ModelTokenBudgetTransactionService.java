package lab.stoneshelter.services;

import java.time.LocalDate;
import java.util.UUID;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
public class ModelTokenBudgetTransactionService {
    private final ModelTokenBudgetRepository repository;
    public ModelTokenBudgetTransactionService(ModelTokenBudgetRepository repository) { this.repository = repository; }
    public ModelTokenReservationEntity reserve(UUID id, LocalDate day, long tokens, long limit) { return repository.reserve(id, day, tokens, limit); }
    public ModelTokenReservationEntity settle(UUID id, long actual) { return repository.settle(id, actual); }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void probe() { repository.probe(); }
}
