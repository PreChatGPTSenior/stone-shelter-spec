package lab.stoneshelter.repositories;

import java.io.IOException;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.concurrent.TimeUnit;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Call;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class OllamaEmbeddingRepository {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StoneDocumentEmbeddingEntity embed(StoneDocumentEmbeddingInputEntity stoneDocumentEmbeddingInputEntity, LongConsumer usage) {
        if (Thread.currentThread().isInterrupted()) {
            throw new StoneDocumentDependencyUnavailableException("The document embedding request was interrupted.");
        }
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(stoneDocumentEmbeddingInputEntity.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(stoneDocumentEmbeddingInputEntity.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(stoneDocumentEmbeddingInputEntity.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(stoneDocumentEmbeddingInputEntity.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build();
        try {
            long deadlineNanos = System.nanoTime() + stoneDocumentEmbeddingInputEntity.getRequestTimeout().toNanos();
            String profile = findProfile(okHttpClient, stoneDocumentEmbeddingInputEntity, deadlineNanos);
            Request request = new Request.Builder().url(stoneDocumentEmbeddingInputEntity.getEndpoint())
                    .post(RequestBody.create(jsonMapper.writeValueAsString(Map.of(
                            "model", StoneDocumentEmbeddingInputEntity.MODEL,
                            "input", stoneDocumentEmbeddingInputEntity.getText(),
                            "dimensions", StoneDocumentEmbeddingInputEntity.DIMENSIONS,
                            "truncate", false)), MediaType.get("application/json")))
                    .build();
            try (Response response = execute(okHttpClient.newCall(request), deadlineNanos)) {
                if (response.body() == null) {
                    throw new StoneDocumentDependencyUnavailableException("The embedding provider returned an empty response.");
                }
                JsonNode jsonNode = jsonMapper.readTree(response.body().string());
                if (!response.isSuccessful()) {
                    if (response.code() == 400 && jsonNode.path("error").asText("").contains("input length exceeds")) {
                        throw new IllegalArgumentException("The document embedding input exceeds the model capacity.");
                    }
                    throw new StoneDocumentDependencyUnavailableException("The document embedding provider is unavailable.");
                }
                if (!profile.equals(findProfile(okHttpClient, stoneDocumentEmbeddingInputEntity, deadlineNanos))) {
                    throw new StoneDocumentDependencyUnavailableException("The embedding model changed during the request.");
                }
                JsonNode inputTokens = jsonNode.path("prompt_eval_count");
                if (StoneDocumentEmbeddingInputEntity.MODEL.equals(jsonNode.path("model").asString())
                        && inputTokens.isIntegralNumber() && inputTokens.canConvertToInt() && inputTokens.asInt() > 0 && inputTokens.asInt() <= 32768) {
                    usage.accept(inputTokens.asInt());
                } else {
                    throw new StoneDocumentDependencyUnavailableException("The embedding provider returned incompatible token accounting.");
                }
                return embedding(jsonNode, profile);
            }
        } catch (IOException exception) {
            throw new StoneDocumentDependencyUnavailableException("The document embedding request failed or timed out.", exception);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new StoneDocumentDependencyUnavailableException("The embedding provider returned invalid JSON.", exception);
        } finally {
            okHttpClient.connectionPool().evictAll();
            okHttpClient.dispatcher().executorService().shutdown();
        }
    }

    private Response execute(Call call, long deadlineNanos) throws IOException {
        call.timeout().deadlineNanoTime(deadlineNanos);
        return call.execute();
    }

    private String findProfile(OkHttpClient okHttpClient,
            StoneDocumentEmbeddingInputEntity stoneDocumentEmbeddingInputEntity, long deadlineNanos) throws IOException {
        Request request = new Request.Builder()
                .url(stoneDocumentEmbeddingInputEntity.getEndpoint().replaceFirst("/embed$", "/tags")).get().build();
        try (Response response = execute(okHttpClient.newCall(request), deadlineNanos)) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new StoneDocumentDependencyUnavailableException("The embedding model metadata is unavailable.");
            }
            for (JsonNode model : jsonMapper.readTree(response.body().string()).path("models")) {
                if (StoneDocumentEmbeddingInputEntity.MODEL.equals(model.path("name").asText())
                        && !model.path("digest").asText("").isBlank()) {
                    return StoneDocumentEmbeddingInputEntity.PROFILE + "/" + model.path("digest").asText();
                }
            }
            throw new StoneDocumentDependencyUnavailableException("The selected embedding model is not installed.");
        }
    }

    private StoneDocumentEmbeddingEntity embedding(JsonNode jsonNode, String profile) {
        JsonNode embeddings = jsonNode.path("embeddings");
        JsonNode inputTokens = jsonNode.path("prompt_eval_count");
        if (!StoneDocumentEmbeddingInputEntity.MODEL.equals(jsonNode.path("model").asText())
                || !embeddings.isArray() || embeddings.size() != 1 || !embeddings.get(0).isArray()
                || embeddings.get(0).size() != StoneDocumentEmbeddingInputEntity.DIMENSIONS
                || !inputTokens.isIntegralNumber() || !inputTokens.canConvertToInt() || inputTokens.asInt() <= 0) {
            throw new StoneDocumentDependencyUnavailableException("The embedding provider returned an incompatible model, vector or token count.");
        }
        double[] vector = new double[StoneDocumentEmbeddingInputEntity.DIMENSIONS];
        boolean nonzero = false;
        for (int index = 0; index < vector.length; index++) {
            JsonNode coordinate = embeddings.get(0).get(index);
            if (!coordinate.isNumber() || !Double.isFinite(coordinate.asDouble())) {
                throw new StoneDocumentDependencyUnavailableException("The embedding provider returned a non-finite or nonnumeric vector.");
            }
            vector[index] = coordinate.asDouble();
            nonzero |= vector[index] != 0;
        }
        if (!nonzero) {
            throw new StoneDocumentDependencyUnavailableException("The embedding provider returned a zero vector.");
        }
        return new StoneDocumentEmbeddingEntity(StoneDocumentEmbeddingInputEntity.MODEL,
                profile, vector, inputTokens.asInt());
    }
}
