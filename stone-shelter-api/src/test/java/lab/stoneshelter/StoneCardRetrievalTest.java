package lab.stoneshelter;

import java.util.List;
import lab.stoneshelter.criteria.StoneCardSearchCriteria;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneCardEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneCardSearchResultEntity;
import lab.stoneshelter.exceptions.InvalidStoneCardRequestException;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneCardSearchResultEntityToStoneCardSearchResponseMapper;
import lab.stoneshelter.repositories.StoneCardEmbeddingRepository;
import lab.stoneshelter.services.StoneCardRetrievalService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.shared.StoneCardSearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class StoneCardRetrievalTest extends IntegrationTest {
    @Autowired private StoneCardRetrievalService service;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private StoneDocumentEmbeddingService embeddings;
    @MockitoSpyBean private StoneCardEmbeddingRepository repository;

    @AfterEach
    void cleanup() { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id BETWEEN 18001 AND 18004"); }

    @Test
    void inclusiveThresholdOrderingTieLimitAndQueryProfile() {
        seed(18002, "profile", vector(1)); seed(18001, "profile", vector(1));
        seed(18003, "profile", vector(1, 1, 1, 1)); seed(18004, "profile", vector(0, 1));
        when(embeddings.embed(any())).thenAnswer(invocation -> {
            StoneDocumentEmbeddingCriteria stoneDocumentEmbeddingCriteria = invocation.getArgument(0);
            assertThat(stoneDocumentEmbeddingCriteria.query()).isTrue();
            assertThat(stoneDocumentEmbeddingCriteria.text()).isEqualTo("basalt");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new StoneDocumentEmbeddingEntity("model", "profile", vector(1), 5);
        });
        assertThat(service.search(new StoneCardSearchRequest("basalt", 4)).matches())
                .extracting(match -> match.stoneId()).containsExactly(18001L, 18002L, 18003L);
        assertThat(service.search(new StoneCardSearchRequest("basalt", 4)).matches().getLast().score()).isEqualTo(0.5);
        assertThat(service.search(new StoneCardSearchRequest("basalt", 2)).matches())
                .extracting(match -> match.stoneId()).containsExactly(18001L, 18002L);
        assertThat(repository.search(new StoneCardSearchCriteria(vector(1), "profile", 4, 1)).matches()).hasSize(2);
    }

    @Test
    void emptyIndexNoQualifyingMatchesAndMapperValidation() {
        assertThat(repository.search(new StoneCardSearchCriteria(vector(1), "profile", 3, 0.5)).matches()).isEmpty();
        seed(18001, "profile", vector(0, 1));
        assertThat(repository.search(new StoneCardSearchCriteria(vector(1), "profile", 3, 0.5)).matches()).isEmpty();
        StoneCardSearchResultEntityToStoneCardSearchResponseMapper mapper = new StoneCardSearchResultEntityToStoneCardSearchResponseMapper();
        assertThat(mapper.toDto(new StoneCardSearchResultEntity(List.of())).matches()).isEmpty();
        assertThatThrownBy(() -> mapper.toDto(null)).isInstanceOf(MapperValidationException.class);
    }

    @Test
    void invalidQueryAndLimitFailBeforeDependencies() {
        for (StoneCardSearchRequest request : new StoneCardSearchRequest[] { null,
                new StoneCardSearchRequest(null, 1), new StoneCardSearchRequest(" ", 1),
                new StoneCardSearchRequest("basalt", 0), new StoneCardSearchRequest("basalt", -1) }) {
            assertThatThrownBy(() -> service.search(request)).isInstanceOf(InvalidStoneCardRequestException.class);
        }
        verifyNoInteractions(embeddings, repository);
    }

    @Test
    void incompatibleProfileProviderFailureAndDatabaseFailureAreNotEmptyResults() {
        seed(18001, "other", vector(1));
        assertThatThrownBy(() -> repository.search(new StoneCardSearchCriteria(vector(1), "profile", 1, 0.5)))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        when(embeddings.embed(any())).thenThrow(new StoneDocumentDependencyUnavailableException("Provider unavailable."));
        assertThatThrownBy(() -> service.search(new StoneCardSearchRequest("basalt", 1)))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        doReturn(new StoneDocumentEmbeddingEntity("model", "profile", vector(1), 5)).when(embeddings).embed(any());
        doThrow(new DataAccessResourceFailureException("Database unavailable.")).when(repository).search(any());
        assertThatThrownBy(() -> service.search(new StoneCardSearchRequest("basalt", 1)))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class).hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    private void seed(long id, String profile, double[] vector) {
        StoneCardEmbeddingEntity stoneCardEmbeddingEntity = new StoneCardEmbeddingEntity();
        stoneCardEmbeddingEntity.setStoneId(id); stoneCardEmbeddingEntity.setText("Card");
        stoneCardEmbeddingEntity.setEmbeddingProfile(profile); stoneCardEmbeddingEntity.setEmbedding(vector);
        repository.save(stoneCardEmbeddingEntity);
    }

    private double[] vector(double... values) {
        double[] vector = new double[1024]; System.arraycopy(values, 0, vector, 0, values.length); return vector;
    }
}
