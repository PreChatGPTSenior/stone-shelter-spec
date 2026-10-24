package lab.stoneshelter;

import java.util.List;
import java.util.function.Consumer;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.InvalidStoneCardRequestException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.services.StoneCardEmbeddingService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import lab.stoneshelter.services.StoneCardReindexRunnerService;
import lab.stoneshelter.services.StoneIndexConsumerService;
import lab.stoneshelter.services.StoneIndexKafkaTopicService;
import lab.stoneshelter.services.OutboxKafkaTopicService;
import lab.stoneshelter.services.OutboxDispatcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class StoneCardEmbeddingServiceTest extends IntegrationTest {
    @MockitoBean private OutboxDispatcherService isolatedOutboxDispatcherService;
    @MockitoBean private OutboxKafkaTopicService isolatedOutboxKafkaTopicService;
    @MockitoBean private StoneIndexKafkaTopicService isolatedStoneIndexKafkaTopicService;
    @MockitoBean private StoneIndexConsumerService isolatedStoneIndexConsumerService;
    @MockitoBean private StoneCardReindexRunnerService isolatedStoneCardReindexRunnerService;
    @Autowired private StoneCardEmbeddingService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private StoneDocumentEmbeddingService embeddings;
    @MockitoSpyBean private StoneCardEmbeddingRepository repository;

    @AfterEach
    void cleanup() { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id = 18001"); }

    @Test
    void providerWorkSuspendsAmbientTransactionAndSeparateWriteCommitsReplacement() {
        when(embeddings.embed(any())).thenAnswer(invocation -> {
            StoneDocumentEmbeddingCriteria stoneDocumentEmbeddingCriteria = invocation.getArgument(0);
            assertThat(stoneDocumentEmbeddingCriteria.query()).isFalse();
            assertThat(stoneDocumentEmbeddingCriteria.text()).contains("Name: Basalt Buddy");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            double[] vector = new double[1024]; vector[0] = 1;
            return new StoneDocumentEmbeddingEntity("model", "profile", vector, 5);
        });
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return invocation.callRealMethod();
        }).when(repository).save(any());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.save(request()).stoneId()).isEqualTo(18001);
            status.setRollbackOnly();
        });
        StoneCardSaveRequest stoneCardSaveRequest = request(); stoneCardSaveRequest.setBiography("Changed.");
        service.save(stoneCardSaveRequest);
        assertThat(jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 18001", String.class)).endsWith("Biography: Changed.");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stone_card_embedding WHERE stone_id = 18001", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidSnapshotsFailBeforeProviderOrPersistence() {
        assertThatThrownBy(() -> service.save(null)).isInstanceOf(InvalidStoneCardRequestException.class);
        List<Consumer<StoneCardSaveRequest>> mutations = List.of(
                input -> input.setStoneId(null), input -> input.setStoneId(0L), input -> input.setName(" "),
                input -> input.setName("x".repeat(121)), input -> input.setStoneType(null),
                input -> input.setStoneSize(null), input -> input.setBiography("x".repeat(2049)));
        for (Consumer<StoneCardSaveRequest> mutation : mutations) {
            StoneCardSaveRequest stoneCardSaveRequest = request(); mutation.accept(stoneCardSaveRequest);
            assertThatThrownBy(() -> service.save(stoneCardSaveRequest)).isInstanceOf(InvalidStoneCardRequestException.class);
        }
        verifyNoInteractions(embeddings, repository);
    }

    @Test
    void embeddingFailurePreservesPriorProjectionAndDatabaseFailureIsNotSuccess() {
        jdbcTemplate.update("INSERT INTO stone_card_embedding VALUES (18001, 'prior', 'profile', ?::vector)", vectorText());
        when(embeddings.embed(any())).thenThrow(new StoneDocumentDependencyUnavailableException("Provider unavailable."));
        assertThatThrownBy(() -> service.save(request())).isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        verifyNoInteractions(repository);
        double[] vector = new double[1024]; vector[0] = 1;
        doReturn(new StoneDocumentEmbeddingEntity("model", "profile", vector, 5)).when(embeddings).embed(any());
        doThrow(new DataAccessResourceFailureException("Database unavailable.")).when(repository).save(any());
        assertThatThrownBy(() -> service.save(request())).isInstanceOf(StoneDocumentDependencyUnavailableException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT text FROM stone_card_embedding WHERE stone_id = 18001", String.class)).isEqualTo("prior");
    }

    private String vectorText() { return "[1," + "0,".repeat(1022) + "0]"; }

    private StoneCardSaveRequest request() {
        StoneCardSaveRequest stoneCardSaveRequest = new StoneCardSaveRequest();
        stoneCardSaveRequest.setStoneId(18001L); stoneCardSaveRequest.setName("Basalt Buddy");
        stoneCardSaveRequest.setStoneType(StoneType.BASALT); stoneCardSaveRequest.setStoneSize(StoneSize.SMALL);
        return stoneCardSaveRequest;
    }
}
