package lab.stoneshelter.repositories;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.StoneDocumentSourceEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentSourceNotFoundException;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Repository;

@Repository
public class MinioStoneDocumentSourceRepository {
    public static final String BUCKET = "stone-encyclopedia";
    public static final String OBJECT = "stone-encyclopedia_en.md";
    private final StoneDocumentIndexSettings settings;

    public MinioStoneDocumentSourceRepository(StoneDocumentIndexSettings settings) { this.settings = settings; }

    public StoneDocumentSourceEntity read(Instant deadline) {
        long remaining = Duration.between(Instant.now(), deadline).toMillis();
        if (remaining < 1 || Thread.currentThread().isInterrupted()) {
            throw new StoneDocumentDependencyUnavailableException("The document source deadline has expired.");
        }
        long timeout = Math.min(settings.sourceReadTimeout().toMillis(), remaining);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
                .connectTimeout(Math.min(settings.connectTimeout().toMillis(), timeout), TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS).callTimeout(timeout, TimeUnit.MILLISECONDS).build();
        try (var stream = MinioClient.builder().endpoint(settings.minioEndpoint())
                .credentials(settings.minioAccessKey(), settings.minioSecretKey()).region("us-east-1")
                .httpClient(client).build().getObject(GetObjectArgs.builder().bucket(BUCKET).object(OBJECT).build())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted() || !Instant.now().isBefore(deadline)) {
                    throw new StoneDocumentDependencyUnavailableException("The document source deadline has expired.");
                }
                if ((long) output.size() + count > settings.maxSourceBytes()) {
                    throw new StoneDocumentSourceUnindexableException("The source byte limit is exceeded.");
                }
                output.write(buffer, 0, count);
            }
            return new StoneDocumentSourceEntity(output.toByteArray(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(output.toByteArray())));
        } catch (StoneDocumentSourceUnindexableException | StoneDocumentDependencyUnavailableException exception) {
            throw exception;
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse().code().equals("NoSuchKey") || exception.errorResponse().code().equals("NoSuchBucket")) {
                throw new StoneDocumentSourceNotFoundException();
            }
            throw new StoneDocumentDependencyUnavailableException("The document source is unavailable.", exception);
        } catch (Exception exception) {
            throw new StoneDocumentDependencyUnavailableException("The document source is unavailable.", exception);
        } finally {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}
