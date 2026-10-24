package lab.stoneshelter.mappers.entities;

import java.time.Clock;
import java.time.Duration;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import org.springframework.stereotype.Component;

@Component
public class StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper
        extends AbstractEntityMapper<StoneDocumentEmbeddingCriteria, StoneDocumentEmbeddingInputEntity> {
    private final StoneDocumentIndexSettings stoneDocumentIndexSettings;
    private final Clock clock;

    public StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper(
            StoneDocumentIndexSettings stoneDocumentIndexSettings, Clock clock) {
        this.stoneDocumentIndexSettings = stoneDocumentIndexSettings;
        this.clock = clock;
    }

    @Override
    protected StoneDocumentEmbeddingInputEntity newEntity() {
        return new StoneDocumentEmbeddingInputEntity();
    }

    @Override
    protected StoneDocumentEmbeddingInputEntity populateEntity(StoneDocumentEmbeddingCriteria stoneDocumentEmbeddingCriteria,
            StoneDocumentEmbeddingInputEntity stoneDocumentEmbeddingInputEntity) {
        Duration remaining = Duration.between(clock.instant(), stoneDocumentEmbeddingCriteria.deadline());
        if (remaining.isNegative() || remaining.isZero() || remaining.toMillis() < 1) {
            throw new StoneDocumentDependencyUnavailableException("The document embedding deadline has expired.");
        }
        stoneDocumentEmbeddingInputEntity.setEndpoint(stoneDocumentIndexSettings.ollamaEndpoint());
        stoneDocumentEmbeddingInputEntity.setText(stoneDocumentEmbeddingCriteria.query()
                ? StoneDocumentEmbeddingInputEntity.QUERY_INSTRUCTION + stoneDocumentEmbeddingCriteria.text()
                : stoneDocumentEmbeddingCriteria.text());
        stoneDocumentEmbeddingInputEntity.setRequestTimeout(minimum(remaining, stoneDocumentIndexSettings.embeddingTimeout()));
        stoneDocumentEmbeddingInputEntity.setConnectTimeout(
                minimum(stoneDocumentEmbeddingInputEntity.getRequestTimeout(), stoneDocumentIndexSettings.connectTimeout()));
        return stoneDocumentEmbeddingInputEntity;
    }

    private Duration minimum(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
