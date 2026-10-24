package lab.stoneshelter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import lab.stoneshelter.entities.StonePhotoDraftEntity;
import lab.stoneshelter.exceptions.PhotoStorageUnavailableException;
import lab.stoneshelter.repositories.StonePhotoDraftEntityRepository;
import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import lab.stoneshelter.entities.StonePhotoEntity;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.LinkedMultiValueMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import javax.imageio.ImageIO;
import lab.stoneshelter.enums.PhotoStorageBucket;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.services.PhotoCleanupService;
import lab.stoneshelter.services.PhotoCleanupJobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StonePhotoDraftIntegrationTest extends IntegrationTest {
    private static final GenericContainer<?> MINIO = new GenericContainer<>(new ImageFromDockerfile("stone-shelter-minio:RELEASE.2025-04-22T22-12-26Z", false)
            .withFileFromPath(".", Path.of("..", "infra", "minio")))
            .withEnv("MINIO_ROOT_USER", "draft-test")
            .withEnv("MINIO_ROOT_PASSWORD", "draft-test-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    static { MINIO.start(); }

    @DynamicPropertySource
    static void photoProperties(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        registry.add("stone.photos.enabled", () -> true);
        registry.add("stone.photos.endpoint", () -> endpoint);
        registry.add("stone.photos.browser-endpoint", () -> endpoint);
        registry.add("stone.photos.bucket", () -> "draft-test-permanent");
        registry.add("stone.photos.draft-bucket", () -> "draft-test-external");
        registry.add("stone.photos.access-key", () -> "draft-test");
        registry.add("stone.photos.secret-key", () -> "draft-test-secret");
        registry.add("stone.photos.cleanup-cron", () -> "-");
        registry.add("stone.photos.cleanup-interval-ms", () -> 0);
    }

    @Value("${local.server.port}") private int serverPort;
    @Autowired private RestTestClient client;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PhotoCleanupService cleanup;
    @Autowired private PhotoCleanupJobService cleanupJob;
    @MockitoSpyBean private MinioPhotoStorageService storage;
    @MockitoSpyBean private Clock clock;
    @MockitoSpyBean private StonePhotoDraftEntityRepository draftRepository;
    @MockitoSpyBean private StonePhotoEntityRepository photoRepository;
    private final List<Long> createdIds = new ArrayList<>();
    private final List<String> draftKeys = new ArrayList<>();
    private final List<String> permanentKeys = new ArrayList<>();

    @AfterEach
    void removeOwnData() {
        reset(storage, clock, draftRepository, photoRepository);
        for (long id : createdIds) {
            permanentKeys.addAll(jdbc.queryForList("SELECT object_key FROM stone_photo WHERE stone_id = ?", String.class, id));
            draftKeys.addAll(jdbc.queryForList("SELECT object_key FROM stone_photo_draft WHERE stone_id = ?", String.class, id));
            jdbc.update("DELETE FROM stone WHERE id = ?", id);
        }
        for (String key : draftKeys) {
            storage.remove(PhotoStorageBucket.DRAFT, key);
            jdbc.update("DELETE FROM stone_photo_draft WHERE object_key = ?", key);
            jdbc.update("DELETE FROM photo_cleanup WHERE object_key = ? AND bucket_type = 'DRAFT'", key);
        }
        for (String key : permanentKeys) {
            storage.remove(key);
            jdbc.update("DELETE FROM photo_cleanup WHERE object_key = ? AND bucket_type = 'PERMANENT'", key);
        }
    }

    @Test
    void twoBucketsPreserveExactCopiedBytesAndIsolateEqualKeys() throws Exception {
        String key = "foundation/" + UUID.randomUUID();
        draftKeys.add(key);
        permanentKeys.add(key);
        byte[] bytes = image("png");
        storage.upload(PhotoStorageBucket.DRAFT, key, bytes, "image/png");
        storage.copy(key, key);
        assertThat(download(storage.readUrl(key)).body()).isEqualTo(bytes);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, key, 3600)).body()).isEqualTo(bytes);
        storage.remove(PhotoStorageBucket.DRAFT, key);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, key, 3600)).statusCode()).isEqualTo(404);
        assertThat(download(storage.readUrl(key)).body()).isEqualTo(bytes);
        jdbc.update("INSERT INTO photo_cleanup(object_key, bucket_type, available_at) VALUES (?, 'DRAFT', now()), (?, 'PERMANENT', now())", key, key);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ?", Long.class, key)).isEqualTo(2);
    }

    @Test
    void migrationPreservesNumericIdentityAndAssignsDistinctStorageUuids() {
        long first = createStone(Map.of());
        long second = createStone(Map.of());
        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT storage_uuid FROM stone WHERE id = ?", UUID.class, first))
                .isNotNull().isNotEqualTo(jdbc.queryForObject("SELECT storage_uuid FROM stone WHERE id = ?", UUID.class, second));
    }

    @ParameterizedTest
    @MethodSource("validDraftImages")
    void draftFormatsHaveUsablePreviewAndExactlyOneDayLifetime(String mediaType, byte[] bytes) throws Exception {
        var draft = uploadDraft(mediaType, bytes, 201);
        assertThat(Duration.between(Instant.parse(draft.get("uploadedAt").toString()), Instant.parse(draft.get("expiresAt").toString())))
                .isEqualTo(Duration.ofHours(24));
        assertThat(Instant.parse(draft.get("uploadedAt").toString()).getNano()).isZero();
        var httpUrl = okhttp3.HttpUrl.get(draft.get("url").toString());
        assertThat(Instant.from(io.minio.Time.AMZ_DATE_FORMAT.parse(httpUrl.queryParameter("X-Amz-Date")))
                .plusSeconds(Long.parseLong(httpUrl.queryParameter("X-Amz-Expires"))))
                .isBeforeOrEqualTo(Instant.parse(draft.get("expiresAt").toString()));
        assertThat(download(draft.get("url").toString()).body()).isEqualTo(bytes);
        assertThat(draft.get("url").toString()).contains("draft-test-external/draft/");
        var refreshed = preview(draft.get("id").toString(), 200);
        assertThat(refreshed).containsEntry("id", draft.get("id")).containsEntry("expiresAt", draft.get("expiresAt"))
                .containsEntry("uploadedAt", draft.get("uploadedAt"));
        assertThat(download(refreshed.get("url").toString()).body()).isEqualTo(bytes);
        assertThat(jdbc.queryForObject("SELECT stone_id FROM stone_photo_draft WHERE id = ?", Long.class, UUID.fromString(draft.get("id").toString()))).isNull();
    }

    static java.util.stream.Stream<Arguments> validDraftImages() throws Exception {
        return java.util.stream.Stream.of(Arguments.of("image/png", image("png")), Arguments.of("image/jpeg", image("jpeg")),
                Arguments.of("image/webp", java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=")),
                Arguments.of("image/png", java.util.Arrays.copyOf(image("png"), 10 * 1024 * 1024)));
    }

    @Test
    void invalidDraftFilesAndUnknownReferencesAreRejected() throws Exception {
        uploadDraft("image/png", new byte[0], 400);
        uploadDraft("image/png", new byte[] {1, 2, 3}, 400);
        uploadDraft("image/png", new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 400);
        uploadDraft("image/png", new byte[10 * 1024 * 1024 + 1], 413);
        uploadDraft("text/plain", image("png"), 415);
        uploadDraft("image/jpeg", image("png"), 415);
        client.post().uri("/api/v1/stone-photo-drafts").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(new LinkedMultiValueMap<String, Object>()).exchange().expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        preview("malformed", 400);
        preview(UUID.randomUUID().toString(), 404);
    }

    @Test
    void expiryIsEnforcedWithoutPhysicalDeletionOrLifetimeRenewal() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        Instant expiresAt = Instant.parse(draft.get("expiresAt").toString());
        doReturn(expiresAt.minusSeconds(60)).when(clock).instant();
        var refreshed = preview(draft.get("id").toString(), 200);
        assertThat(refreshed.get("url").toString()).contains("X-Amz-Expires=60");
        assertThat(refreshed).containsEntry("expiresAt", draft.get("expiresAt"));
        doReturn(expiresAt.minusNanos(1)).when(clock).instant();
        assertThat(preview(draft.get("id").toString(), 200).get("url").toString()).contains("X-Amz-Expires=1");
        doReturn(expiresAt).when(clock).instant();
        preview(draft.get("id").toString(), 404);
        assertThat(download(draft.get("url").toString()).statusCode()).isEqualTo(200);
    }

    @Test
    void draftStorageFailuresPreserveExpirationAndOrphanCleanup() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        doThrow(new PhotoStorageUnavailableException()).when(storage).checkObject(eq(PhotoStorageBucket.DRAFT), anyString());
        preview(draft.get("id").toString(), 503);
        reset(storage);
        assertThat(preview(draft.get("id").toString(), 200)).containsEntry("expiresAt", draft.get("expiresAt"));
        doThrow(new PhotoStorageUnavailableException()).when(storage).upload(eq(PhotoStorageBucket.DRAFT), anyString(), any(byte[].class), anyString());
        uploadDraft("image/png", image("png"), 503);
        reset(storage);
        doThrow(new DataIntegrityViolationException("Simulated draft persistence failure")).when(draftRepository).saveAndFlush(any(StonePhotoDraftEntity.class));
        uploadDraft("image/png", image("png"), 500);
        assertThat(draftKeys).hasSize(3);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, draftKeys.getLast(), 3600)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone_photo_draft WHERE object_key = ?", Long.class, draftKeys.getLast())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ? AND bucket_type = 'DRAFT'", Long.class, draftKeys.getLast())).isEqualTo(1);
    }

    @Test
    void initialGalleryAcceptsSixteenPhotosInRequestOrderAndLegacyRequestsStayValid() throws Exception {
        Instant serverTime = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        doReturn(serverTime).when(clock).instant();
        var uploadIds = new ArrayList<String>();
        for (int i = 0; i < 16; i++) uploadIds.add(uploadDraft("image/png", image("png"), 201).get("id").toString());
        java.util.Collections.reverse(uploadIds);
        long id = createStone(Map.of("photoUploadIds", uploadIds));
        var detail = detail(id);
        assertThat(detail).containsEntry("admissionDate", serverTime.toString());
        var photos = photos(detail);
        assertThat(photos).hasSize(16);
        assertThat(photos).extracting(photo -> photo.get("position")).containsExactlyElementsOf(java.util.stream.IntStream.range(0,16).boxed().toList());
        assertThat(detail.get("photo")).isEqualTo(photos.getFirst().get("url"));
        assertThat(jdbc.queryForList("SELECT draft_id FROM stone_photo WHERE stone_id = ? ORDER BY position", UUID.class, id))
                .containsExactlyElementsOf(uploadIds.stream().map(UUID::fromString).toList());
        for (String uploadId : uploadIds) preview(uploadId, 404);
        assertThat(photos(detail(createStone(Map.of())))).isEmpty();
        var nullIds = new java.util.HashMap<String, Object>();
        nullIds.put("photoUploadIds", null);
        nullIds.put("photo", "  legacy  ");
        var legacy = detail(createStone(nullIds));
        assertThat(legacy).containsEntry("photo", "  legacy  ");
        assertThat(photos(legacy)).isEmpty();
        assertThat(photos(detail(createStone(Map.of("photoUploadIds", List.of()))))).isEmpty();
    }

    @Test
    void creationRejectsInvalidReferencesAtomicallyAndAllowsRetryWithoutReupload() throws Exception {
        String uploadId = uploadDraft("image/png", image("png"), 201).get("id").toString();
        postStone(Map.of("name", " ", "photoUploadIds", List.of(uploadId)), 400);
        postStone(Map.of("photoUploadIds", List.of(uploadId, uploadId)), 400);
        postStone(Map.of("photoUploadIds", java.util.Arrays.asList(uploadId, null)), 400);
        postStone(Map.of("photoUploadIds", List.of("malformed")), 400);
        postStone(Map.of("photoUploadIds", List.of(uploadId, UUID.randomUUID().toString())), 400);
        postStone(Map.of("photoUploadIds", java.util.stream.IntStream.range(0,17).mapToObj(i -> UUID.randomUUID().toString()).toList()), 400);
        assertThat(preview(uploadId, 200)).containsEntry("id", uploadId);
        long id = createStone(Map.of("photoUploadIds", List.of(uploadId)));
        assertThat(photos(detail(id))).hasSize(1);
        postStone(Map.of("photoUploadIds", List.of(uploadId)), 409);
    }

    @Test
    void expiryAndPersistenceFailureLeaveAllOtherDraftsReusable() throws Exception {
        var first = uploadDraft("image/png", image("png"), 201);
        var second = uploadDraft("image/png", image("png"), 201);
        jdbc.update("UPDATE stone_photo_draft SET expires_at = '2000-01-01T00:00:00Z' WHERE id = ?", UUID.fromString(first.get("id").toString()));
        postStone(Map.of("photoUploadIds", List.of(second.get("id"), first.get("id"))), 400);
        preview(second.get("id").toString(), 200);
        doThrow(new DataIntegrityViolationException("Simulated association failure")).when(photoRepository).save(any(StonePhotoEntity.class));
        postStone(Map.of("photoUploadIds", List.of(second.get("id"))), 500);
        reset(photoRepository);
        preview(second.get("id").toString(), 200);
        assertThat(photos(detail(createStone(Map.of("photoUploadIds", List.of(second.get("id"))))))).hasSize(1);
    }

    @Test
    void concurrentCreationConsumesDraftReferencesOnlyOnce() throws Exception {
        var first = uploadDraft("image/png", image("png"), 201).get("id");
        var second = uploadDraft("image/png", image("png"), 201).get("id");
        var responses = new ArrayList<Map<String, Object>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(executor.submit(() -> rawStone(Map.of("photoUploadIds", List.of(first, second)))),
                    executor.submit(() -> rawStone(Map.of("photoUploadIds", List.of(second, first)))));
            for (var future : futures) responses.add(future.get(20, TimeUnit.SECONDS));
        }
        assertThat(responses.stream().filter(response -> response.containsKey("id")).count()).isEqualTo(1);
        assertThat(responses.stream().filter(response -> Integer.valueOf(409).equals(response.get("status"))).count()).isEqualTo(1);
        for (var response : responses) if (response.get("id") instanceof Number id) createdIds.add(id.longValue());
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT stone_id) FROM stone_photo_draft WHERE id IN (?,?)", Long.class,
                UUID.fromString(first.toString()), UUID.fromString(second.toString()))).isEqualTo(1);
    }

    @Test
    void unavailablePermanentImagesUseTheBackendPlaceholder() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        doThrow(new PhotoStorageUnavailableException()).when(storage).copy(anyString(), anyString());
        long id = createStone(Map.of("photoUploadIds", List.of(draft.get("id"))));
        var photo = photos(detail(id)).getFirst();
        assertThat(photo.get("url")).isEqualTo("/images/placeholder-rock.png");
        byte[] placeholder = client.get().uri(photo.get("url").toString()).exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.IMAGE_PNG).expectBody(byte[].class).returnResult().getResponseBody();
        assertThat(placeholder).isEqualTo(java.nio.file.Files.readAllBytes(Path.of("..", "specs", "0005-ui-mock-to-code", "placeholder-rock.png")));
    }

    @Test
    void copyingStartsAfterCommitAndResponseContainsPermanentBytesUnderStorageUuid() throws Exception {
        byte[] bytes = image("png");
        var draft = uploadDraft("image/png", bytes, 201);
        doAnswer(invocation -> {
            String targetKey = invocation.getArgument(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stone_photo p JOIN stone s ON s.id = p.stone_id WHERE p.object_key = ?", Long.class, targetKey)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT stone_id FROM stone_photo_draft WHERE id = ?", Long.class, UUID.fromString(draft.get("id").toString()))).isNotNull();
            return invocation.callRealMethod();
        }).when(storage).copy(anyString(), anyString());
        var created = postStone(Map.of("photoUploadIds", List.of(draft.get("id"))), 201);
        long id = ((Number) created.get("id")).longValue();
        createdIds.add(id);
        String expectedKey = jdbc.queryForObject("SELECT storage_uuid FROM stone WHERE id = ?", UUID.class, id) + "/" + draft.get("id");
        assertThat(jdbc.queryForObject("SELECT object_key FROM stone_photo WHERE stone_id = ?", String.class, id)).isEqualTo(expectedKey);
        assertThat(jdbc.queryForObject("SELECT copy_ready FROM stone_photo WHERE stone_id = ?", Boolean.class, id)).isTrue();
        assertThat(photos(created).getFirst().get("url").toString()).contains("draft-test-permanent/" + expectedKey);
        assertThat(download(photos(created).getFirst().get("url").toString()).body()).isEqualTo(bytes);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, draftKeys.getFirst(), 3600)).body()).isEqualTo(bytes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ? AND bucket_type = 'PERMANENT'", Long.class, expectedKey)).isZero();
        var catalog = client.post().uri("/api/v1/stones/search").body(Map.of()).exchange().expectStatus().isOk().expectBody(JSON_OBJECT).returnResult().getResponseBody();
        @SuppressWarnings("unchecked") var items = (List<Map<String, Object>>) catalog.get("content");
        assertThat(items.stream().filter(item -> ((Number)item.get("id")).longValue() == id).findFirst().orElseThrow().get("photo").toString()).contains(expectedKey);
    }

    @Test
    void partialCopyFailureKeepsSourceAndDoesNotPreventOtherCopies() throws Exception {
        var first = uploadDraft("image/png", image("png"), 201);
        var second = uploadDraft("image/jpeg", image("jpeg"), 201);
        doThrow(new PhotoStorageUnavailableException()).when(storage).copy(eq("draft/" + first.get("id")), anyString());
        long id = createStone(Map.of("photoUploadIds", List.of(first.get("id"), second.get("id"))));
        var gallery = photos(detail(id));
        assertThat(gallery).hasSize(2);
        assertThat(gallery.getFirst().get("url")).isEqualTo("/images/placeholder-rock.png");
        assertThat(download(gallery.get(1).get("url").toString()).body()).isEqualTo(image("jpeg"));
        assertThat(jdbc.queryForList("SELECT copy_ready FROM stone_photo WHERE stone_id = ? ORDER BY position", Boolean.class, id)).containsExactly(false, true);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, "draft/" + first.get("id"), 3600)).statusCode()).isEqualTo(200);
        assertThat(detail(id)).containsEntry("adoptionStatus", "AVAILABLE");
    }

    @Test
    void copyReadinessPersistenceFailureStillReturnsCreatedAndRetainsDraft() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        doThrow(new DataIntegrityViolationException("Simulated copy readiness failure")).when(photoRepository).saveAndFlush(any(StonePhotoEntity.class));
        long id = createStone(Map.of("photoUploadIds", List.of(draft.get("id"))));
        assertThat(photos(detail(id)).getFirst().get("url")).isEqualTo("/images/placeholder-rock.png");
        assertThat(jdbc.queryForObject("SELECT copy_ready FROM stone_photo WHERE stone_id = ?", Boolean.class, id)).isFalse();
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, "draft/" + draft.get("id"), 3600)).statusCode()).isEqualTo(200);
    }

    @Test
    void internalCopyLookupFailureStillReturnsCreatedWithProtectedSource() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        doThrow(new DataIntegrityViolationException("Simulated copy lookup failure")).when(photoRepository).findIdsByStoneId(org.mockito.ArgumentMatchers.anyLong());
        long id = createStone(Map.of("photoUploadIds", List.of(draft.get("id"))));
        assertThat(photos(detail(id)).getFirst().get("url")).isEqualTo("/images/placeholder-rock.png");
        assertThat(jdbc.queryForObject("SELECT stone_id FROM stone_photo_draft WHERE id = ?", Long.class,
                UUID.fromString(draft.get("id").toString()))).isEqualTo(id);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, "draft/" + draft.get("id"), 3600)).statusCode()).isEqualTo(200);
        verify(storage, never()).copy(anyString(), anyString());
    }

    @Test
    void failedStoneTransactionDoesNotStartCopying() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        doThrow(new DataIntegrityViolationException("Simulated association failure")).when(photoRepository).save(any(StonePhotoEntity.class));
        postStone(Map.of("photoUploadIds", List.of(draft.get("id"))), 500);
        verify(storage, never()).copy(anyString(), anyString());
    }

    @Test
    void unassociatedDraftsExpireAtTheBoundaryAndArePhysicallyCleaned() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        String key = "draft/" + draft.get("id");
        cleanupJob.run();
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, key, 3600)).statusCode()).isEqualTo(200);
        doReturn(Instant.parse(draft.get("expiresAt").toString())).when(clock).instant();
        postStone(Map.of("photoUploadIds", List.of(draft.get("id"))), 400);
        cleanupJob.run();
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, key, 3600)).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone_photo_draft WHERE id = ?", Long.class, UUID.fromString(draft.get("id").toString()))).isZero();
    }

    @Test
    void confirmedCopiesReleaseDraftsButFailedCopiesSurviveBeyondOneDay() throws Exception {
        var ready = uploadDraft("image/png", image("png"), 201);
        long readyStone = createStone(Map.of("photoUploadIds", List.of(ready.get("id"))));
        cleanupJob.run();
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, "draft/" + ready.get("id"), 3600)).statusCode()).isEqualTo(404);
        assertThat(download(photos(detail(readyStone)).getFirst().get("url").toString()).statusCode()).isEqualTo(200);
        postStone(Map.of("photoUploadIds", List.of(ready.get("id"))), 409);
        assertThat(jdbc.queryForObject("SELECT draft_id FROM stone_photo WHERE stone_id = ?", UUID.class, readyStone))
                .isEqualTo(UUID.fromString(ready.get("id").toString()));
        var failed = uploadDraft("image/png", image("png"), 201);
        doThrow(new PhotoStorageUnavailableException()).when(storage).copy(anyString(), anyString());
        long failedStone = createStone(Map.of("photoUploadIds", List.of(failed.get("id"))));
        doReturn(Instant.parse(failed.get("expiresAt").toString()).plusSeconds(86400)).when(clock).instant();
        cleanupJob.run();
        cleanupJob.run();
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, "draft/" + failed.get("id"), 3600)).statusCode()).isEqualTo(200);
        assertThat(photos(detail(failedStone)).getFirst().get("url")).isEqualTo("/images/placeholder-rock.png");
        assertThat(jdbc.queryForObject("SELECT stone_id FROM stone_photo_draft WHERE id = ?", Long.class, UUID.fromString(failed.get("id").toString()))).isEqualTo(failedStone);
    }

    @Test
    void stoneDeletionReclaimsPermanentAndRetainedDraftObjects() throws Exception {
        var first = uploadDraft("image/png", image("png"), 201);
        var second = uploadDraft("image/jpeg", image("jpeg"), 201);
        doThrow(new PhotoStorageUnavailableException()).when(storage).copy(eq("draft/" + first.get("id")), anyString());
        long id = createStone(Map.of("photoUploadIds", List.of(first.get("id"), second.get("id"))));
        var keys = jdbc.queryForList("SELECT object_key FROM stone_photo WHERE stone_id = ?", String.class, id);
        permanentKeys.addAll(keys);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(id);
        cleanupJob.run();
        for (String key : keys) assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(404);
        for (Object uploadId : List.of(first.get("id"), second.get("id"))) {
            assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, "draft/" + uploadId, 3600)).statusCode()).isEqualTo(404);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone_photo_draft WHERE stone_id = ?", Long.class, id)).isZero();
    }

    @Test
    void draftCleanupIsBucketSpecificAndStopsOnStorageFailureWithoutLosingWork() throws Exception {
        var first = uploadDraft("image/png", image("png"), 201);
        String key = "draft/" + first.get("id");
        permanentKeys.add(key);
        storage.upload(key, image("jpeg"), "image/jpeg");
        doReturn(Instant.parse(first.get("expiresAt").toString())).when(clock).instant();
        doThrow(new PhotoStorageUnavailableException()).when(storage).remove(eq(PhotoStorageBucket.DRAFT), eq(key));
        cleanupJob.run();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ? AND bucket_type = 'DRAFT'", Long.class, key)).isEqualTo(1);
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, key, 3600)).statusCode()).isEqualTo(200);
        reset(storage);
        cleanupJob.run();
        assertThat(download(storage.readUrl(PhotoStorageBucket.DRAFT, key, 3600)).statusCode()).isEqualTo(404);
        assertThat(download(storage.readUrl(key)).body()).isEqualTo(image("jpeg"));
    }

    @Test
    void concurrentCleanupCannotDeleteASourceAcceptedForCreation() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        String key = "draft/" + draft.get("id");
        jdbc.update("UPDATE photo_cleanup SET available_at = '2000-01-01T00:00:00Z' WHERE object_key = ? AND bucket_type = 'DRAFT'", key);
        Map<String, Object> created;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var clean = executor.submit(() -> cleanup.cleanDraft(key));
            var create = executor.submit(() -> rawStone(Map.of("photoUploadIds", List.of(draft.get("id")))));
            created = create.get(20, TimeUnit.SECONDS);
            clean.get(20, TimeUnit.SECONDS);
        }
        assertThat(created).containsKey("id");
        createdIds.add(((Number)created.get("id")).longValue());
        assertThat(download(photos(created).getFirst().get("url").toString()).body()).isEqualTo(image("png"));
    }

    @Test
    void aDraftCanBeAssociatedImmediatelyBeforeExpiryButNotAtExpiry() throws Exception {
        var draft = uploadDraft("image/png", image("png"), 201);
        doReturn(Instant.parse(draft.get("expiresAt").toString()).minusSeconds(1)).when(clock).instant();
        assertThat(photos(detail(createStone(Map.of("photoUploadIds", List.of(draft.get("id"))))))).hasSize(1);
    }

    @Test
    void operatorLoaderCreatesTheWholeSuppliedDatasetThroughRealHttpEndpoints() throws Exception {
        var previousKeys = jdbc.queryForList("SELECT object_key FROM stone_photo_draft", String.class);
        var process = new ProcessBuilder("python3", "../scripts/populate_stones.py", "--base-url", "http://localhost:" + serverPort)
                .redirectErrorStream(true).start();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var matcher = java.util.regex.Pattern.compile("Created ([A-Z]+): id=(\\d+); photos=(\\d+)").matcher(output);
        var loadedTypes = new java.util.HashSet<String>();
        while (matcher.find()) {
            long id = Long.parseLong(matcher.group(2));
            createdIds.add(id);
            loadedTypes.add(matcher.group(1));
        }
        for (String key : jdbc.queryForList("SELECT object_key FROM stone_photo_draft", String.class)) {
            if (!previousKeys.contains(key)) draftKeys.add(key);
        }
        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("Completed: 10 stones, 124 photographs");
        assertThat(loadedTypes).hasSize(10);
        assertThat(createdIds).hasSize(10);
        int photoCount = 0;
        for (long id : createdIds) {
            var stone = detail(id);
            String stoneType = stone.get("stoneType").toString();
            var gallery = photos(stone);
            assertThat(gallery).hasSize(stoneType.equals("LIMESTONE") ? 16 : 12);
            photoCount += gallery.size();
            String folder = stoneType.substring(0,1) + stoneType.substring(1).toLowerCase(java.util.Locale.ROOT);
            for (var photo : gallery) {
                assertThat(download(photo.get("url").toString()).body()).isEqualTo(java.nio.file.Files.readAllBytes(
                        Path.of("..", "specs", "0008-add-stone-api", "stone_test_img", folder, String.format("%02d.png", ((Number)photo.get("position")).intValue()+1))));
            }
        }
        assertThat(photoCount).isEqualTo(124);
    }

    private Map<String, Object> detail(long id) {
        return client.get().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> photos(Map<String, Object> response) { return (List<Map<String, Object>>) response.get("photos"); }

    private static Map<String, Object> stoneBody(Map<String, Object> extra) {
        var body = new java.util.HashMap<String, Object>(Map.of("name", "Draft Stone " + UUID.randomUUID(), "stoneType", "LIMESTONE",
                "stoneSize", "SMALL", "adoptionStatus", "AVAILABLE"));
        body.putAll(extra);
        return body;
    }

    private Map<String, Object> rawStone(Map<String, Object> extra) {
        return client.post().uri("/api/v1/stones").body(stoneBody(extra)).exchange()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    private Map<String, Object> postStone(Map<String, Object> extra, int expectedStatus) {
        var body = stoneBody(extra);
        var response = client.post().uri("/api/v1/stones").body(body).exchange().expectStatus().isEqualTo(expectedStatus);
        if (expectedStatus != 201) {
            response.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stone WHERE name = ?", Long.class, body.get("name"))).isZero();
        } else response.expectHeader().doesNotExist("Location");
        return response.expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    private Map<String, Object> uploadDraft(String mediaType, byte[] bytes, int expectedStatus) {
        var previousKeys = jdbc.queryForList("SELECT object_key FROM photo_cleanup WHERE bucket_type = 'DRAFT'", String.class);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "draft-image"; }
        }, headers));
        var response = client.post().uri("/api/v1/stone-photo-drafts").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).exchange();
        for (String key : jdbc.queryForList("SELECT object_key FROM photo_cleanup WHERE bucket_type = 'DRAFT'", String.class)) {
            if (!previousKeys.contains(key)) draftKeys.add(key);
        }
        response.expectStatus().isEqualTo(expectedStatus);
        if (expectedStatus == 201) response.expectHeader().doesNotExist("Location");
        else response.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        return response.expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    private Map<String, Object> preview(String id, int expectedStatus) {
        var response = client.get().uri("/api/v1/stone-photo-drafts/" + id).exchange().expectStatus().isEqualTo(expectedStatus);
        if (expectedStatus != 200) response.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        return response.expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    private long createStone(Map<String, Object> extra) {
        var response = postStone(extra, 201);
        long id = ((Number) response.get("id")).longValue();
        createdIds.add(id);
        return id;
    }

    private static byte[] image(String format) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    private static HttpResponse<byte[]> download(String url) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
