package lab.stoneshelter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import lab.stoneshelter.entities.StonePhotoEntity;
import lab.stoneshelter.exceptions.PhotoStorageUnavailableException;
import lab.stoneshelter.repositories.StonePhotoEntityRepository;
import lab.stoneshelter.repositories.PhotoCleanupEntityRepository;
import lab.stoneshelter.services.MinioPhotoStorageService;
import lab.stoneshelter.services.PhotoCleanupService;
import lab.stoneshelter.services.PhotoCleanupJobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StonePhotoIntegrationTest extends IntegrationTest {
    private static final GenericContainer<?> MINIO = new GenericContainer<>(new ImageFromDockerfile("stone-shelter-minio:RELEASE.2025-04-22T22-12-26Z", false)
            .withFileFromPath(".", Path.of("..", "infra", "minio")))
            .withEnv("MINIO_ROOT_USER", "gallery-test")
            .withEnv("MINIO_ROOT_PASSWORD", "gallery-test-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    private static final byte[] WEBP = Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=");
    static { MINIO.start(); }

    @DynamicPropertySource
    static void photoProperties(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        registry.add("stone.photos.enabled", () -> true);
        registry.add("stone.photos.endpoint", () -> endpoint);
        registry.add("stone.photos.browser-endpoint", () -> endpoint);
        registry.add("stone.photos.bucket", () -> "gallery-test");
        registry.add("stone.photos.access-key", () -> "gallery-test");
        registry.add("stone.photos.secret-key", () -> "gallery-test-secret");
        registry.add("stone.photos.cleanup-cron", () -> "-");
        registry.add("stone.photos.cleanup-interval-ms", () -> 0);
    }

    @Autowired private RestTestClient client;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PhotoCleanupService cleanup;
    @Autowired private PhotoCleanupJobService job;
    @MockitoSpyBean private MinioPhotoStorageService storage;
    @MockitoSpyBean private StonePhotoEntityRepository photoRepository;
    @MockitoSpyBean private PhotoCleanupEntityRepository cleanupRepository;
    private final List<Long> createdIds = new ArrayList<>();
    private final List<String> extraKeys = new ArrayList<>();

    @AfterEach
    void removeOwnData() {
        reset(storage, photoRepository, cleanupRepository);
        for (long id : createdIds) {
            if (jdbc.queryForObject("SELECT count(*) FROM stone WHERE id = ?", Long.class, id) != 0) {
                client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk();
            }
        }
        for (String objectKey : extraKeys) {
            storage.remove(objectKey);
            jdbc.update("DELETE FROM photo_cleanup WHERE object_key = ?", objectKey);
        }
        for (long id : createdIds) {
            jdbc.update("UPDATE photo_cleanup SET available_at = '2000-01-01T00:00:00Z' WHERE object_key LIKE ?", "stones/" + id + "/%");
        }
        cleanup.findDueKeys(1000).forEach(cleanup::clean);
    }

    @Test
    void realStorageGalleryReadAndLegacyUpdateUseTheFirstSuccessfulCover() throws Exception {
        long id = createStone();
        assertThat(detail(id)).containsEntry("photo", "legacy-photo").containsEntry("photos", List.of());
        byte[] bytes = image("png");
        var first = upload(id, "image/png", bytes, 201);
        var second = upload(id, "image/jpeg", image("jpeg"), 201);
        assertThat(first).containsOnlyKeys("id", "url", "addedAt", "position").containsEntry("position", 0);
        assertThat(second).containsEntry("position", 1);
        assertThat(first.get("id")).isNotEqualTo(second.get("id"));
        assertThat(Instant.parse(first.get("addedAt").toString())).isBeforeOrEqualTo(Instant.parse(second.get("addedAt").toString()));
        var photos = photos(detail(id));
        assertThat(photos).extracting(photo -> photo.get("id")).containsExactly(first.get("id"), second.get("id"));
        assertThat(detail(id).get("photo").toString()).contains(objectKey(id, 0));
        String url = photos.getFirst().get("url").toString();
        assertThat(url).contains("X-Amz-Expires=3600");
        var downloaded = download(url);
        assertThat(downloaded.statusCode()).isEqualTo(200);
        assertThat(downloaded.body()).isEqualTo(bytes);
        assertThat(download(url.substring(0, url.indexOf('?'))).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT photo_url FROM stone WHERE id = ?", String.class, id)).isEqualTo("legacy-photo");
        assertThat(objectKey(id, 0)).doesNotContain("http", "X-Amz");
        var replacement = stoneBody();
        replacement.put("photo", "replacement-legacy");
        replacement.put("admissionDate", detail(id).get("admissionDate"));
        client.put().uri("/api/v1/stones/" + id).body(replacement).exchange().expectStatus().isOk();
        assertThat(photos(detail(id))).extracting(photo -> photo.get("id")).containsExactly(first.get("id"), second.get("id"));
        var search = client.post().uri("/api/v1/stones/search").body(Map.of()).exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
        @SuppressWarnings("unchecked") var content = (List<Map<String, Object>>) search.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.getFirst()).doesNotContainKey("photos");
        assertThat(content.getFirst().get("photo").toString()).contains(objectKey(id, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isZero();
    }

    @ParameterizedTest
    @MethodSource("validImages")
    void supportedImageFormatsReachMinio(String mediaType, byte[] bytes) throws Exception {
        long id = createStone();
        var response = upload(id, mediaType, bytes, 201);
        assertThat(download(response.get("url").toString()).body()).isEqualTo(bytes);
    }

    static java.util.stream.Stream<Arguments> validImages() throws Exception {
        return java.util.stream.Stream.of(Arguments.of("image/png", image("png")),
                Arguments.of("image/jpeg", image("jpeg")), Arguments.of("image/webp", WEBP));
    }

    @Test
    void concurrentUploadsHaveUniqueStablePositionsEvenWhenTimestampsTie() throws Exception {
        long id = createStone();
        byte[] bytes = image("png");
        List<Map<String, Object>> responses = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<Map<String, Object>>>();
            for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> upload(id, "image/png", bytes, 201)));
            for (var future : futures) responses.add(future.get());
        }
        assertThat(responses).extracting(photo -> photo.get("position")).containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0, 12).boxed().toList());
        jdbc.update("UPDATE stone_photo SET added_at = '2000-01-01T00:00:00Z' WHERE stone_id = ?", id);
        var ordered = photos(detail(id));
        assertThat(ordered).extracting(photo -> photo.get("position")).containsExactlyElementsOf(java.util.stream.IntStream.range(0, 12).boxed().toList());
        assertThat(photos(detail(id))).extracting(photo -> photo.get("id")).containsExactlyElementsOf(ordered.stream().map(photo -> photo.get("id")).toList());
        assertThat(detail(id).get("photo").toString()).contains(objectKey(id, 0));
    }

    @Test
    void invalidUploadsAndUnknownStoneNeverAcquirePositions() throws Exception {
        long id = createStone();
        upload(id, "image/png", new byte[0], 400);
        upload(id, "image/png", new byte[] {1, 2, 3}, 400);
        upload(id, "image/png", new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 400);
        upload(id, "image/jpeg", new byte[] {(byte) 255, (byte) 216, (byte) 255}, 400);
        upload(id, "image/webp", java.util.Arrays.copyOf(WEBP, 25), 400);
        upload(id, "image/webp", java.util.HexFormat.of().parseHex("5249464612000000574542505650384c050000002f0000000000"), 400);
        upload(id, "text/plain", image("png"), 415);
        upload(id, "image/jpeg", image("png"), 415);
        upload(id, "image/png", new byte[10 * 1024 * 1024 + 1], 413);
        var multipart = new LinkedMultiValueMap<String, Object>();
        client.post().uri("/api/v1/stones/" + id + "/photos").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart).exchange().expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk();
        upload(id, "image/png", image("png"), 404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone_photo WHERE stone_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isZero();
    }

    @Test
    void unavailableStorageReturns503AndRetainsNoVisibleMetadata() throws Exception {
        long id = createStone();
        doThrow(new PhotoStorageUnavailableException()).when(storage).upload(anyString(), any(byte[].class), anyString());
        upload(id, "image/png", image("png"), 503);
        assertThat(photos(detail(id))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isEqualTo(1);
        verify(storage, never()).remove(anyString());
        reset(storage);
        assertThat(upload(id, "image/png", image("png"), 201)).containsEntry("position", 0);
    }

    @Test
    void databaseFailureRetainsIntentForDailyCleanupWithoutImmediateRemoval() throws Exception {
        long id = createStone();
        doThrow(new DataIntegrityViolationException("Simulated metadata failure")).when(photoRepository)
                .saveAndFlush(any(StonePhotoEntity.class));
        upload(id, "image/png", image("png"), 500);
        assertThat(photos(detail(id))).isEmpty();
        String key = jdbc.queryForObject("SELECT object_key FROM photo_cleanup", String.class);
        extraKeys.add(key);
        verify(storage, never()).remove(anyString());
        assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(200);
        job.run();
        verify(storage, never()).remove(anyString());
        reset(photoRepository);
        jdbc.update("UPDATE photo_cleanup SET available_at = '2000-01-01T00:00:00Z' WHERE object_key = ?", key);
        job.run();
        assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ?", Long.class, key)).isZero();
    }

    @Test
    void unavailableReadinessSkipsAllQueuedObjectsAndLaterRunCleansThem() throws Exception {
        long id = createStone();
        upload(id, "image/png", image("png"), 201);
        String key = objectKey(id, 0);
        extraKeys.add(key);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk();
        doThrow(new PhotoStorageUnavailableException()).when(storage).checkAvailability();
        job.run();
        verify(storage, never()).remove(anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ?", Long.class, key)).isEqualTo(1);
        assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(200);
        reset(storage);
        job.run();
        assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(404);
    }

    @Test
    void stoneRemovalDefersOwnedFilesAndMidRunFailureRetainsWorkUntilLaterDailyRun() throws Exception {
        long id = createStone();
        upload(id, "image/png", image("png"), 201);
        upload(id, "image/png", image("png"), 201);
        var keys = jdbc.queryForList("SELECT object_key FROM stone_photo WHERE stone_id = ? ORDER BY position", String.class, id);
        extraKeys.addAll(keys);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
        verify(storage, never()).remove(anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone_photo WHERE stone_id = ?", Long.class, id)).isZero();
        doThrow(new PhotoStorageUnavailableException()).when(storage).remove(anyString());
        job.run();
        verify(storage).remove(anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isEqualTo(2);
        for (String key : keys) assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(200);
        reset(storage);
        job.run();
        for (String key : keys) assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isZero();
    }

    @Test
    void cleanupDatabaseFailureDoesNotAffectDeletedStoneAndLaterRunRetainsProgress() throws Exception {
        long id = createStone();
        upload(id, "image/png", image("png"), 201);
        String key = objectKey(id, 0);
        extraKeys.add(key);
        doThrow(new DataIntegrityViolationException("Simulated cleanup query failure")).when(cleanupRepository)
                .findByObjectKeyForUpdate(key);
        client.delete().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
        job.run();
        verify(storage, never()).remove(anyString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stone WHERE id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup WHERE object_key = ?", Long.class, key)).isEqualTo(1);
        reset(cleanupRepository);
        job.run();
        assertThat(download(storage.readUrl(key)).statusCode()).isEqualTo(404);
    }

    @Test
    void cleanupReadinessProbeUsesTheRealConfiguredMinioEndpoint() {
        storage.checkAvailability();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isZero();
    }

    @Test
    void staleCleanupIntentNeverDeletesAnAttachedPhotoAndAbandonedUploadsAreReclaimed() throws Exception {
        long id = createStone();
        upload(id, "image/png", image("png"), 201);
        String attached = objectKey(id, 0);
        jdbc.update("INSERT INTO photo_cleanup(object_key, available_at) VALUES (?, '2000-01-01T00:00:00Z')", attached);
        cleanup.clean(attached);
        assertThat(download(storage.readUrl(attached)).statusCode()).isEqualTo(200);
        assertThat(photos(detail(id))).hasSize(1);
        String abandoned = "stones/" + id + "/abandoned";
        extraKeys.add(abandoned);
        cleanup.recordUploadIntent(abandoned);
        storage.upload(abandoned, image("png"), "image/png");
        jdbc.update("UPDATE photo_cleanup SET available_at = '2000-01-01T00:00:00Z' WHERE object_key = ?", abandoned);
        job.run();
        assertThat(download(storage.readUrl(abandoned)).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM photo_cleanup", Long.class)).isZero();
    }

    private long createStone() {
        var response = client.post().uri("/api/v1/stones").body(stoneBody()).exchange().expectStatus().isCreated()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
        long id = ((Number) response.get("id")).longValue();
        createdIds.add(id);
        return id;
    }

    private static Map<String, Object> stoneBody() {
        return new HashMap<>(Map.of("name", "Gallery Stone", "stoneType", "BASALT", "stoneSize", "SMALL",
                "adoptionStatus", "AVAILABLE", "photo", "legacy-photo"));
    }

    private Map<String, Object> upload(long id, String mediaType, byte[] bytes, int expectedStatus) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "stone-image"; }
        }, headers));
        var response = client.post().uri("/api/v1/stones/" + id + "/photos")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).exchange().expectStatus().isEqualTo(expectedStatus);
        if (expectedStatus != 201) response.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        var result = response.expectBody(JSON_OBJECT).returnResult().getResponseBody();
        if (expectedStatus != 201) assertThat(result).containsEntry("status", expectedStatus).containsKeys("title", "detail");
        return result;
    }

    private Map<String, Object> detail(long id) {
        return client.get().uri("/api/v1/stones/" + id).exchange().expectStatus().isOk()
                .expectBody(JSON_OBJECT).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> photos(Map<String, Object> detail) {
        return (List<Map<String, Object>>) detail.get("photos");
    }

    private String objectKey(long id, int position) {
        return jdbc.queryForObject("SELECT object_key FROM stone_photo WHERE stone_id = ? AND position = ?", String.class, id, position);
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
