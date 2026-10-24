package lab.stoneshelter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingEntity;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.mappers.entities.StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper;
import lab.stoneshelter.repositories.OllamaEmbeddingRepository;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneDocumentIndexSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.env.StandardEnvironment;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "stone.document.qualification", matches = "true")
class StoneDocumentEmbeddingQualificationTest {
    @Test
    void qualifiesInstalledModelDimensionsTokensAndRepresentativeRetrieval() throws Exception {
        StoneDocumentIndexSettings stoneDocumentIndexSettings = new StoneDocumentIndexSettings(new StandardEnvironment());
        StoneDocumentEmbeddingService stoneDocumentEmbeddingService = new StoneDocumentEmbeddingService(
                new OllamaEmbeddingRepository(), new StoneDocumentEmbeddingCriteriaToStoneDocumentEmbeddingInputEntityMapper(
                        stoneDocumentIndexSettings, Clock.systemUTC()));
        Instant deadline = Instant.now().plus(stoneDocumentIndexSettings.importTimeout());
        int contextLength = modelContextLength(stoneDocumentIndexSettings);
        String source = Files.readString(Path.of("../stone-rules/stone-encyclopedias/stone-encyclopedia_en.md"));
        List<String> passages = List.of(
                passage(source, "Basalt", "Monthly Care"),
                passage(source, "Basalt", "Fictional Care Ritual"),
                passage(source, "Granite", "Psychological Vibe"),
                passage(source, "Obsidian", "Fictional Care Ritual"),
                passage(source, "Rating Scales", "Care Demand Scale"),
                passage(source, "General Care Rules", "Cleaning Principles"));
        List<StoneDocumentEmbeddingEntity> documentEmbeddings = new ArrayList<>();
        for (int index = 0; index < passages.size(); index++) {
            StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity = stoneDocumentEmbeddingService.embed(
                    new StoneDocumentEmbeddingCriteria(passages.get(index), false, deadline));
            validate(stoneDocumentEmbeddingEntity);
            if (!documentEmbeddings.isEmpty()) {
                assertThat(stoneDocumentEmbeddingEntity.profile()).isEqualTo(documentEmbeddings.getFirst().profile());
            }
            documentEmbeddings.add(stoneDocumentEmbeddingEntity);
            System.out.println("Passage " + index + ": estimatedTokens="
                    + stoneDocumentEmbeddingService.estimateTokens(passages.get(index))
                    + "; providerTokens=" + stoneDocumentEmbeddingEntity.inputTokens());
        }
        List<String> queries = List.of("How do I clean basalt every month?",
                "A quiet companion that enjoys shared silence",
                "Which stone likes keeping everything organized?",
                "How can I give obsidian its monthly evening ritual?",
                "Does a higher care rating mean daily chores?",
                "Can I use acidic cleaner on marble or limestone?");
        for (int index = 0; index < queries.size(); index++) {
            StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity = stoneDocumentEmbeddingService.embed(
                    new StoneDocumentEmbeddingCriteria(queries.get(index), true, deadline));
            validate(stoneDocumentEmbeddingEntity);
            assertThat(stoneDocumentEmbeddingEntity.profile()).isEqualTo(documentEmbeddings.getFirst().profile());
            double expectedScore = cosine(stoneDocumentEmbeddingEntity.vector(), documentEmbeddings.get(index).vector());
            int betterPassages = 0;
            for (StoneDocumentEmbeddingEntity documentEmbedding : documentEmbeddings) {
                if (cosine(stoneDocumentEmbeddingEntity.vector(), documentEmbedding.vector()) > expectedScore) {
                    betterPassages++;
                }
            }
            assertThat(betterPassages).as("Representative query %s finds its predefined passage in the first five", queries.get(index))
                    .isLessThan(5);
            if (index == 0) {
                assertThat(expectedScore).isGreaterThan(cosine(stoneDocumentEmbeddingEntity.vector(), documentEmbeddings.get(3).vector()));
            }
            System.out.println("Representative query " + index + ": expectedPassageRank=" + (betterPassages + 1)
                    + "; providerTokens=" + stoneDocumentEmbeddingEntity.inputTokens());
        }
        assertThatThrownBy(() -> stoneDocumentEmbeddingService.embed(new StoneDocumentEmbeddingCriteria(
                " stone".repeat(contextLength + 1), false, deadline)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("model capacity");
        System.out.println("Qualified embedding profile: " + documentEmbeddings.getFirst().profile()
                + "; dimensions=1024; advertisedContext=" + contextLength + "; overCapacityRejected=true");
    }

    private int modelContextLength(StoneDocumentIndexSettings stoneDocumentIndexSettings) throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(stoneDocumentIndexSettings.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(stoneDocumentIndexSettings.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false).build();
        try (Response response = okHttpClient.newCall(new Request.Builder()
                .url(stoneDocumentIndexSettings.ollamaEndpoint().replaceFirst("/embed$", "/show"))
                .post(RequestBody.create(JsonMapper.builder().build().writeValueAsString(
                        Map.of("model", StoneDocumentEmbeddingInputEntity.MODEL)), MediaType.get("application/json")))
                .build()).execute()) {
            assertThat(response.code()).as("The selected embedding model must already be installed").isEqualTo(200);
            assertThat(response.body()).isNotNull();
            JsonNode modelInfo = JsonMapper.builder().build().readTree(response.body().string()).path("model_info");
            String architecture = modelInfo.path("general.architecture").asText();
            assertThat(modelInfo.path(architecture + ".embedding_length").asInt()).isEqualTo(1024);
            int contextLength = modelInfo.path(architecture + ".context_length").asInt();
            assertThat(contextLength).isBetween(1024, 131072);
            return contextLength;
        } finally {
            okHttpClient.connectionPool().evictAll();
            okHttpClient.dispatcher().executorService().shutdown();
        }
    }

    private String passage(String source, String stone, String topic) {
        int stoneStart = source.indexOf("\n## " + stone + "\n");
        int topicStart = source.indexOf("\n### " + topic + "\n", stoneStart);
        assertThat(stoneStart).isNotNegative();
        assertThat(topicStart).isGreaterThan(stoneStart);
        int nextStone = source.indexOf("\n## ", stoneStart + 1);
        assertThat(nextStone < 0 || topicStart < nextStone).isTrue();
        int end = source.indexOf("\n#", topicStart + 1);
        return "Stone Encyclopedia > " + stone + " > " + topic.replace("Fictional Care Ritual", "Care Ritual")
                + "\n" + source.substring(topicStart + topic.length() + 6, end < 0 ? source.length() : end).strip();
    }

    private void validate(StoneDocumentEmbeddingEntity stoneDocumentEmbeddingEntity) {
        assertThat(stoneDocumentEmbeddingEntity.vector()).hasSize(1024);
        assertThat(Arrays.stream(stoneDocumentEmbeddingEntity.vector()).allMatch(Double::isFinite)).isTrue();
        assertThat(stoneDocumentEmbeddingEntity.inputTokens()).isPositive();
        assertThat(stoneDocumentEmbeddingEntity.profile()).startsWith(StoneDocumentEmbeddingInputEntity.PROFILE + "/");
    }

    static double cosine(double[] first, double[] second) {
        double product = 0, firstNorm = 0, secondNorm = 0;
        for (int index = 0; index < first.length; index++) {
            product += first[index] * second[index];
            firstNorm += first[index] * first[index];
            secondNorm += second[index] * second[index];
        }
        return product / Math.sqrt(firstNorm * secondNorm);
    }
}
