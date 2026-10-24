package lab.stoneshelter.services;

import java.util.regex.Matcher;
import java.time.Clock;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import java.util.regex.Pattern;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import lab.stoneshelter.mappers.entities.StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneDocumentEmbeddingService {
    public static final int MAX_INPUT_TOKENS = 32768;
    private ModelInvocationAdmissionService admission;
    private Clock clock;
    @Autowired public void configureAdmission(ModelInvocationAdmissionService admission, Clock clock) { this.admission = admission; this.clock = clock; }
    private static final Pattern TOKEN_PART = Pattern.compile("[\\p{L}\\p{N}]+|[^\\s]");
    private final OllamaEmbeddingRepository ollamaEmbeddingRepository;
    private final StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper
            stoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;

    public StoneDocumentEmbeddingService(OllamaEmbeddingRepository ollamaEmbeddingRepository,
            StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper
                    stoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper) {
        this.ollamaEmbeddingRepository = ollamaEmbeddingRepository;
        this.stoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper =
                stoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public StoneDocumentEmbeddingEntity embed(StoneDocumentEmbeddingCriteria stoneDocumentEmbeddingCriteria) {
        if (stoneDocumentEmbeddingCriteria == null || stoneDocumentEmbeddingCriteria.text() == null
                || stoneDocumentEmbeddingCriteria.text().isBlank() || stoneDocumentEmbeddingCriteria.deadline() == null || stoneDocumentEmbeddingCriteria.admissionMode() == null) {
            throw new IllegalArgumentException("Embedding text and deadline are required.");
        }
        StoneDocumentEmbeddingInputEntity stoneDocumentEmbeddingInputEntity =
                stoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper.toEntity(stoneDocumentEmbeddingCriteria);
        if (admission == null || clock == null) throw new StoneDocumentDependencyUnavailableException("Shared model control is unavailable.");
        try (var lease = admission.acquire(stoneDocumentEmbeddingCriteria.admissionMode(), MAX_INPUT_TOKENS, stoneDocumentEmbeddingCriteria.deadline())) {
            try {
                stoneDocumentEmbeddingInputEntity = stoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper
                        .toEntity(stoneDocumentEmbeddingInputEntity, stoneDocumentEmbeddingCriteria);
            } catch (StoneDocumentDependencyUnavailableException exception) {
                lease.settle(0);
                if (!clock.instant().isBefore(stoneDocumentEmbeddingCriteria.deadline()))
                    throw new ModelAdmissionDeferredException(stoneDocumentEmbeddingCriteria.deadline());
                admission.failure(exception); throw exception;
            }
            try {
                return ollamaEmbeddingRepository.embed(stoneDocumentEmbeddingInputEntity, lease::settle);
            } catch (StoneDocumentDependencyUnavailableException exception) {
                admission.failure(exception); throw exception;
            }
        }
    }

    public int estimateTokens(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Token estimation requires text.");
        }
        Matcher matcher = TOKEN_PART.matcher(text);
        int tokens = 0;
        while (matcher.find()) {
            tokens += (matcher.end() - matcher.start() + 3) / 4;
        }
        return tokens;
    }
}
