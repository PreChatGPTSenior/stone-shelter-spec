package lab.stoneshelter.services;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.StatObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Time;
import io.minio.http.Method;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;
import lab.stoneshelter.enums.PhotoStorageBucket;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import okhttp3.Request;
import lab.stoneshelter.exceptions.PhotoStorageUnavailableException;
import lab.stoneshelter.exceptions.StonePhotoDraftNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class MinioPhotoStorageService {
    private final MinioClient internalClient;
    private final MinioClient browserClient;
    private final MinioClient cleanupClient;
    private final OkHttpClient cleanupHttpClient;
    private final String endpoint;
    private final String bucket;
    private final String draftBucket;
    private final String placeholderUrl;
    private final Set<String> readyBuckets = new HashSet<>();

    public MinioPhotoStorageService(@Value("${stone.photos.enabled}") boolean enabled,
            @Value("${stone.photos.endpoint}") String endpoint,
            @Value("${stone.photos.browser-endpoint}") String browserEndpoint,
            @Value("${stone.photos.bucket}") String bucket,
            @Value("${stone.photos.draft-bucket}") String draftBucket,
            @Value("${stone.photos.placeholder-url}") String placeholderUrl,
            @Value("${stone.photos.access-key}") String accessKey,
            @Value("${stone.photos.secret-key}") String secretKey) {
        if (enabled && (bucket.isBlank() || draftBucket.isBlank() || bucket.equals(draftBucket))) {
            throw new IllegalArgumentException("Photo storage requires distinct nonempty permanent and draft buckets.");
        }
        this.bucket = bucket;
        this.draftBucket = draftBucket;
        this.placeholderUrl = placeholderUrl;
        this.endpoint = endpoint;
        cleanupHttpClient = new OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build();
        cleanupClient = enabled ? MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey)
                .region("us-east-1").httpClient(cleanupHttpClient).build() : null;
        internalClient = enabled ? MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey)
                .region("us-east-1").build() : null;
        browserClient = enabled ? MinioClient.builder().endpoint(browserEndpoint).credentials(accessKey, secretKey)
                .region("us-east-1").build() : null;
    }

    public void upload(String objectKey, byte[] bytes, String mediaType) {
        upload(PhotoStorageBucket.PERMANENT, objectKey, bytes, mediaType);
    }

    public void upload(PhotoStorageBucket bucketType, String objectKey, byte[] bytes, String mediaType) {
        checkEnabled();
        try {
            ensureBucket(bucketName(bucketType));
            internalClient.putObject(PutObjectArgs.builder().bucket(bucketName(bucketType)).object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1).contentType(mediaType).build());
        } catch (Exception exception) {
            throw new PhotoStorageUnavailableException(exception);
        }
    }

    public void checkAvailability() {
        checkEnabled();
        try (var response = cleanupHttpClient.newCall(new Request.Builder()
                .url(endpoint.replaceAll("/+$", "") + "/minio/health/cluster").head().build()).execute()) {
            if (response.code() != 200) throw new PhotoStorageUnavailableException();
        } catch (Exception exception) {
            throw new PhotoStorageUnavailableException(exception);
        }
    }

    public void remove(String objectKey) {
        remove(PhotoStorageBucket.PERMANENT, objectKey);
    }

    public void remove(PhotoStorageBucket bucketType, String objectKey) {
        checkEnabled();
        try {
            cleanupClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName(bucketType)).object(objectKey).build());
        } catch (ErrorResponseException exception) {
            if (!"NoSuchBucket".equals(exception.errorResponse().code())
                    && !"NoSuchKey".equals(exception.errorResponse().code())) {
                throw new PhotoStorageUnavailableException(exception);
            }
        } catch (Exception exception) {
            throw new PhotoStorageUnavailableException(exception);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    public String readUrl(String objectKey) {
        return readUrl(PhotoStorageBucket.PERMANENT, objectKey, 3600);
    }

    public String readUrl(PhotoStorageBucket bucketType, String objectKey, int expirySeconds) {
        checkEnabled();
        try {
            return browserClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucketName(bucketType))
                    .object(objectKey).method(Method.GET).expiry(expirySeconds, TimeUnit.SECONDS).build());
        } catch (Exception exception) {
            throw new PhotoStorageUnavailableException(exception);
        }
    }

    public String readDraftUrl(String objectKey, int expirySeconds, Instant expiresAt) {
        if (expirySeconds < 1) throw new StonePhotoDraftNotFoundException();
        String url = readUrl(PhotoStorageBucket.DRAFT, objectKey, expirySeconds);
        Instant signedAt = Instant.from(Time.AMZ_DATE_FORMAT.parse(HttpUrl.get(url).queryParameter("X-Amz-Date")));
        // Signing can cross the expiration boundary after the service's time check.
        if (signedAt.plusSeconds(expirySeconds).isAfter(expiresAt)) throw new StonePhotoDraftNotFoundException();
        return url;
    }

    private void checkEnabled() {
        if (internalClient == null) throw new PhotoStorageUnavailableException();
    }

    public String placeholderUrl() { return placeholderUrl; }

    public void checkObject(PhotoStorageBucket bucketType, String objectKey) {
        checkEnabled();
        try {
            cleanupClient.statObject(StatObjectArgs.builder().bucket(bucketName(bucketType)).object(objectKey).build());
        } catch (Exception exception) {
            throw new PhotoStorageUnavailableException(exception);
        }
    }

    public void copy(String sourceKey, String targetKey) {
        checkEnabled();
        try {
            ensureBucket(bucket);
            internalClient.copyObject(CopyObjectArgs.builder().bucket(bucket).object(targetKey)
                    .source(CopySource.builder().bucket(draftBucket).object(sourceKey).build()).build());
        } catch (Exception exception) {
            throw new PhotoStorageUnavailableException(exception);
        }
    }

    private String bucketName(PhotoStorageBucket bucketType) {
        return bucketType == PhotoStorageBucket.DRAFT ? draftBucket : bucket;
    }

    private synchronized void ensureBucket(String bucketName) throws Exception {
        if (!readyBuckets.contains(bucketName)) {
            if (!internalClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                internalClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
            readyBuckets.add(bucketName);
        }
    }
}
