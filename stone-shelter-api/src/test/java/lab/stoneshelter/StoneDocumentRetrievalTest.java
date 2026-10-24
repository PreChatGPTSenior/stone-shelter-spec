package lab.stoneshelter;

import java.time.Duration;
import java.util.List;
import lab.stoneshelter.criteria.StoneDocumentSearchCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneDocumentSearchResultEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.mappers.dtos.StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper;
import lab.stoneshelter.repositories.StoneDocumentIndexRepository;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.services.StoneDocumentRetrievalService;
import lab.stoneshelter.shared.StoneDocumentSearchRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class StoneDocumentRetrievalTest extends IntegrationTest {
    @Autowired private StoneDocumentIndexRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stone_document_fragment WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("DELETE FROM stone_document_index WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
    }

    @Test
    void cosineOrderingLimitAndWrapperMappingUseOnlyCompatibleProjection() {
        seed();
        var result = repository.search(new StoneDocumentSearchCriteria(vector(1, 0), "profile", 2));
        assertThat(result.passages()).hasSize(2);
        assertThat(result.passages().getFirst().fragment().getSectionPath()).isEqualTo("Care 2");
        assertThat(result.passages().getFirst().score()).isCloseTo(1, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.passages().getLast().score()).isLessThan(result.passages().getFirst().score());
        var response = new StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper().toDto(result);
        assertThat(response.passages()).hasSize(2);
        assertThat(response.passages().getFirst().text()).isEqualTo("Passage 2");
        assertThatThrownBy(() -> repository.search(new StoneDocumentSearchCriteria(vector(1, 0), "different", 5)))
                .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
    }

    @Test
    void queryProfileAndBoundedResultArePassedThroughService() {
        seed();
        StoneDocumentEmbeddingService embeddings = mock(StoneDocumentEmbeddingService.class);
        StoneDocumentIndexSettings settings = mock(StoneDocumentIndexSettings.class);
        when(settings.embeddingTimeout()).thenReturn(Duration.ofMinutes(30));
        when(embeddings.embed(any())).thenAnswer(invocation -> {
            lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria criteria = invocation.getArgument(0);
            assertThat(criteria.query()).isTrue();
            return new StoneDocumentEmbeddingEntity("model", "profile", vector(1, 0), 5);
        });
        StoneDocumentRetrievalService service = new StoneDocumentRetrievalService(embeddings, repository,
                new StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper(), settings);
        assertThat(service.search(new StoneDocumentSearchRequest("monthly care", 1)).passages()).hasSize(1);
        assertThatThrownBy(() -> service.search(new StoneDocumentSearchRequest("", 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(new StoneDocumentSearchRequest("care", 6))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper().toDto(null))
                .isInstanceOf(MapperValidationException.class);
        assertThat(new StoneDocumentSearchResultEntityToStoneDocumentSearchResponseMapper()
                .toDto(new StoneDocumentSearchResultEntity(List.of())).passages()).isEmpty();
    }

    private void seed() {
        StoneDocumentIndexEntity index = new StoneDocumentIndexEntity();
        index.setDocumentKey(StoneDocumentIndexEntity.DOCUMENT_KEY); index.setSourceVersion("a".repeat(64));
        index.setEmbeddingProfile("profile"); index.setLanguage("en"); index.setIndexedFragments(3); repository.save(index);
        repository.insertFragments(List.of(fragment(0, vector(0, 1)), fragment(1, vector(1, 1)), fragment(2, vector(1, 0))));
    }

    private StoneDocumentFragmentEntity fragment(int order, double[] vector) {
        StoneDocumentFragmentEntity fragment = new StoneDocumentFragmentEntity();
        fragment.setDocumentKey(StoneDocumentIndexEntity.DOCUMENT_KEY); fragment.setSourceVersion("a".repeat(64));
        fragment.setEmbeddingProfile("profile"); fragment.setLanguage("en"); fragment.setFragmentOrder(order);
        fragment.setSectionPath("Care " + order); fragment.setText("Passage " + order); fragment.setEmbedding(vector); return fragment;
    }

    private double[] vector(double first, double second) {
        double[] vector = new double[1024]; vector[0] = first; vector[1] = second; return vector;
    }
}
