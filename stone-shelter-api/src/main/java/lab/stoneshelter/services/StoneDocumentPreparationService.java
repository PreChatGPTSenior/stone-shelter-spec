package lab.stoneshelter.services;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.criteria.StoneMarkdownDocumentCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentSourceEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import lab.stoneshelter.repositories.MinioStoneDocumentSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneDocumentPreparationService {
    private final MinioStoneDocumentSourceRepository sourceRepository;
    private final StoneMarkdownDocumentService markdownService;
    private final StoneDocumentEmbeddingService embeddingService;
    private final StoneDocumentIndexSettings settings;

    public StoneDocumentPreparationService(MinioStoneDocumentSourceRepository sourceRepository,
            StoneMarkdownDocumentService markdownService, StoneDocumentEmbeddingService embeddingService,
            StoneDocumentIndexSettings settings) {
        this.sourceRepository = sourceRepository;
        this.markdownService = markdownService;
        this.embeddingService = embeddingService;
        this.settings = settings;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<StoneDocumentFragmentEntity> prepare(Instant deadline) {
        StoneDocumentSourceEntity stoneDocumentSourceEntity = sourceRepository.read(deadline);
        List<StoneDocumentFragmentEntity> fragments = markdownService.process(new StoneMarkdownDocumentCriteria(
                decode(stoneDocumentSourceEntity.bytes()), stoneDocumentSourceEntity.sourceVersion(), settings.maxFragments()));
        String profile = null;
        for (StoneDocumentFragmentEntity stoneDocumentFragmentEntity : fragments) {
            if (!Instant.now().isBefore(deadline) || Thread.currentThread().isInterrupted()) {
                throw new StoneDocumentDependencyUnavailableException("The document import deadline has expired.");
            }
            StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity;
            try { stoneDocumentEmbeddingEntity = embeddingService.embed(new StoneDocumentEmbeddingCriteria(stoneDocumentFragmentEntity.getText(), false, deadline, ModelAdmissionMode.IMPORT)); }
            catch (ModelAdmissionDeferredException exception) {
                throw new StoneDocumentDependencyUnavailableException("The document import admission deadline has expired.", exception);
            }
            catch (IllegalArgumentException exception) {
                throw new StoneDocumentSourceUnindexableException("A passage exceeds model input capacity.");
            }
            catch (InvalidDataAccessApiUsageException exception) {
                if (exception.getCause() instanceof IllegalArgumentException) {
                    throw new StoneDocumentSourceUnindexableException("A passage exceeds model input capacity.");
                }
                throw exception;
            }
            if (profile != null && !profile.equals(stoneDocumentEmbeddingEntity.profile())) {
                throw new StoneDocumentDependencyUnavailableException("The embedding profile changed during preparation.");
            }
            profile = stoneDocumentEmbeddingEntity.profile();
            stoneDocumentFragmentEntity.setEmbeddingProfile(profile);
            stoneDocumentFragmentEntity.setEmbedding(stoneDocumentEmbeddingEntity.vector());
        }
        return fragments;
    }

    private String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new StoneDocumentSourceUnindexableException("The source is not valid UTF-8.");
        }
    }
}
