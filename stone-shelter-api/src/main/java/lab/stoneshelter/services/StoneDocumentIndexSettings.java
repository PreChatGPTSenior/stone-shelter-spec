package lab.stoneshelter.services;

import java.time.Duration;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import okhttp3.HttpUrl;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoneDocumentIndexSettings {
    private final Environment environment;

    public StoneDocumentIndexSettings(Environment environment) {
        this.environment = environment;
    }

    public String ollamaEndpoint() {
        String endpoint = environment.getProperty("STONE_DOCUMENT_OLLAMA_BASE_URL");
        HttpUrl httpUrl = endpoint == null ? null : HttpUrl.parse(endpoint);
        if (httpUrl == null || !httpUrl.username().isEmpty() || !httpUrl.password().isEmpty()
                || httpUrl.query() != null || httpUrl.fragment() != null) {
            throw new StoneDocumentDependencyUnavailableException("The document embedding endpoint is not configured correctly.");
        }
        return httpUrl.newBuilder().addPathSegments("api/embed").build().toString();
    }

    public Duration connectTimeout() {
        return duration("STONE_DOCUMENT_CONNECT_TIMEOUT", "PT30S");
    }

    public Duration embeddingTimeout() {
        return duration("STONE_DOCUMENT_EMBEDDING_TIMEOUT", "PT30M");
    }

    public Duration importTimeout() {
        return duration("STONE_DOCUMENT_IMPORT_TIMEOUT", "PT2H");
    }

    public String minioEndpoint() {
        String endpoint = required("STONE_DOCUMENT_MINIO_ENDPOINT");
        HttpUrl httpUrl = HttpUrl.parse(endpoint);
        if (httpUrl == null || !httpUrl.username().isEmpty() || !httpUrl.password().isEmpty()
                || httpUrl.query() != null || httpUrl.fragment() != null) {
            throw new StoneDocumentDependencyUnavailableException("The document source endpoint is invalid.");
        }
        return endpoint;
    }

    public String minioAccessKey() { return required("STONE_DOCUMENT_MINIO_ACCESS_KEY"); }
    public String minioSecretKey() { return required("STONE_DOCUMENT_MINIO_SECRET_KEY"); }
    public Duration sourceReadTimeout() { return duration("STONE_DOCUMENT_MINIO_READ_TIMEOUT", "PT5M"); }
    public int maxSourceBytes() { return positiveInt("STONE_DOCUMENT_MAX_SOURCE_BYTES", 1048576); }
    public int maxFragments() { return positiveInt("STONE_DOCUMENT_MAX_FRAGMENTS", 2000); }

    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw new StoneDocumentDependencyUnavailableException(name + " is missing.");
        return value;
    }

    private int positiveInt(String name, int fallback) {
        try {
            int value = Integer.parseInt(environment.getProperty(name, Integer.toString(fallback)));
            if (value < 1 || value == Integer.MAX_VALUE) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException exception) {
            throw new StoneDocumentDependencyUnavailableException(name + " must be a supported positive integer.", exception);
        }
    }

    private Duration duration(String name, String fallback) {
        try {
            Duration duration = Duration.parse(environment.getProperty(name, fallback));
            if (duration.isNegative() || duration.isZero() || duration.toMillis() < 1
                    || duration.toMillis() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException();
            }
            return duration;
        } catch (RuntimeException exception) {
            throw new StoneDocumentDependencyUnavailableException(name + " must be a positive supported duration.", exception);
        }
    }
}
