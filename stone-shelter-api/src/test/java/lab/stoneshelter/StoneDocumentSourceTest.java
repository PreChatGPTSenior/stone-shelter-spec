package lab.stoneshelter;

import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentSourceEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentSourceNotFoundException;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import lab.stoneshelter.mappers.entities.StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper;
import lab.stoneshelter.repositories.MinioStoneDocumentSourceRepository;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import lab.stoneshelter.services.StoneDocumentPreparationService;
import lab.stoneshelter.services.StoneMarkdownDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoneDocumentSourceTest {
    private StoneDocumentIndexSettings settings(String url) {
        return new StoneDocumentIndexSettings(new MockEnvironment()
                .withProperty("STONE_DOCUMENT_MINIO_ENDPOINT", url)
                .withProperty("STONE_DOCUMENT_MINIO_ACCESS_KEY", "test-access")
                .withProperty("STONE_DOCUMENT_MINIO_SECRET_KEY", "test-secret"));
    }

    @Test
    void readsOnlyFixedSourceAndHashesExactBytes() throws Exception {
        String source = "# Guide\r\n## Basalt\r\nAttributes.\r\n";
        AtomicReference<String> path = new AtomicReference<>();
        try (StoneDocumentHttpTestSupport server = new StoneDocumentHttpTestSupport(e -> { })) {
            server.source(e -> { path.set(e.getRequestURI().getPath()); StoneDocumentHttpTestSupport.respond(e, 200, source); });
            StoneDocumentSourceEntity result = new MinioStoneDocumentSourceRepository(settings(server.baseUrl())).read(Instant.now().plusSeconds(10));
            assertThat(path.get()).isEqualTo("/stone-encyclopedia/stone-encyclopedia_en.md");
            assertThat(result.bytes()).isEqualTo(source.getBytes(StandardCharsets.UTF_8));
            assertThat(result.sourceVersion()).isEqualTo(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    void missingAndOversizedSourcesAreDistinctFailures() throws Exception {
        try (StoneDocumentHttpTestSupport server = new StoneDocumentHttpTestSupport(e -> { })) {
            server.source(e -> StoneDocumentHttpTestSupport.respond(e, 404,
                    "<Error><Code>NoSuchKey</Code><Message>Missing</Message></Error>"));
            assertThatThrownBy(() -> new MinioStoneDocumentSourceRepository(settings(server.baseUrl())).read(Instant.now().plusSeconds(10)))
                    .isInstanceOf(StoneDocumentSourceNotFoundException.class);
        }
        try (StoneDocumentHttpTestSupport server = new StoneDocumentHttpTestSupport(e -> { })) {
            server.source(e -> StoneDocumentHttpTestSupport.respond(e, 200, "x".repeat(100)));
            StoneDocumentIndexSettings limits = new StoneDocumentIndexSettings(new MockEnvironment()
                    .withProperty("STONE_DOCUMENT_MINIO_ENDPOINT", server.baseUrl())
                    .withProperty("STONE_DOCUMENT_MINIO_ACCESS_KEY", "test-access")
                    .withProperty("STONE_DOCUMENT_MINIO_SECRET_KEY", "test-secret")
                    .withProperty("STONE_DOCUMENT_MAX_SOURCE_BYTES", "10"));
            assertThatThrownBy(() -> new MinioStoneDocumentSourceRepository(limits).read(Instant.now().plusSeconds(10)))
                    .isInstanceOf(StoneDocumentSourceUnindexableException.class);
        }
    }

    @Test
    void sourceReadTimeoutAndExpiredDeadlineStopReading() throws Exception {
        try (StoneDocumentHttpTestSupport server = new StoneDocumentHttpTestSupport(e -> { })) {
            server.source(e -> { Thread.sleep(300); StoneDocumentHttpTestSupport.respond(e, 200, "source"); });
            StoneDocumentIndexSettings limits = new StoneDocumentIndexSettings(new MockEnvironment()
                    .withProperty("STONE_DOCUMENT_MINIO_ENDPOINT", server.baseUrl())
                    .withProperty("STONE_DOCUMENT_MINIO_ACCESS_KEY", "test-access")
                    .withProperty("STONE_DOCUMENT_MINIO_SECRET_KEY", "test-secret")
                    .withProperty("STONE_DOCUMENT_MINIO_READ_TIMEOUT", "PT0.05S"));
            assertThatThrownBy(() -> new MinioStoneDocumentSourceRepository(limits).read(Instant.now().plusSeconds(10)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
            assertThatThrownBy(() -> new MinioStoneDocumentSourceRepository(limits).read(Instant.now().minusSeconds(1)))
                    .isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        }
    }

    @Test
    void preparesCompleteProfiledFragmentsAndRejectsInvalidUtf8OrGenerationFailure() {
        MinioStoneDocumentSourceRepository source = mock(MinioStoneDocumentSourceRepository.class);
        StoneDocumentEmbeddingService embeddings = mock(StoneDocumentEmbeddingService.class);
        StoneMarkdownDocumentService markdown = new StoneMarkdownDocumentService(new StoneDocumentEmbeddingService(null, null),
                new StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper());
        StoneDocumentPreparationService service = new StoneDocumentPreparationService(source, markdown, embeddings, settings("http://localhost:9000"));
        when(source.read(any())).thenReturn(new StoneDocumentSourceEntity("# Guide\n## Basalt\nAttributes\n### Care\nDust".getBytes(StandardCharsets.UTF_8), "a".repeat(64)));
        double[] vector = new double[1024]; vector[0] = 1;
        when(embeddings.embed(any(StoneDocumentEmbeddingCriteria.class))).thenReturn(new StoneDocumentEmbeddingEntity("model", "profile", vector, 10));
        List<StoneDocumentFragmentEntity> fragments = service.prepare(Instant.now().plusSeconds(10));
        assertThat(fragments).hasSize(2).allSatisfy(f -> {
            assertThat(f.getEmbeddingProfile()).isEqualTo("profile");
            assertThat(f.getEmbedding()).hasSize(1024);
        });
        when(embeddings.embed(any())).thenThrow(new StoneDocumentDependencyUnavailableException("unavailable"));
        assertThatThrownBy(() -> service.prepare(Instant.now().plusSeconds(10))).isInstanceOf(StoneDocumentDependencyUnavailableException.class);
        org.mockito.Mockito.doThrow(new InvalidDataAccessApiUsageException("model capacity", new IllegalArgumentException("input too long")))
                .when(embeddings).embed(any());
        assertThatThrownBy(() -> service.prepare(Instant.now().plusSeconds(10))).isInstanceOf(StoneDocumentSourceUnindexableException.class);
        when(source.read(any())).thenReturn(new StoneDocumentSourceEntity(new byte[]{(byte) 0xff}, "a".repeat(64)));
        assertThatThrownBy(() -> service.prepare(Instant.now().plusSeconds(10))).isInstanceOf(StoneDocumentSourceUnindexableException.class);
    }
}
