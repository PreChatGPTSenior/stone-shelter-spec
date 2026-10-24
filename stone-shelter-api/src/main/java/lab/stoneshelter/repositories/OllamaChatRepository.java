package lab.stoneshelter.repositories;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class OllamaChatRepository {
    public static final String MODEL = "qwen2.5:3b";
    private static final String DIGEST = "357c53fb659c5076de1d65ccb0b397446227b71a42be9d1603d46168015c9e4b";
    private static final String TEMPLATE = "eb4402837c7829a690fa845de4d7f3fd842c2adee476d5341da8a46ea9255175";
    public static final String CLARIFICATION = "{\"action\":\"CLARIFY\",\"criteria\":{\"stoneTypes\":[],\"stoneSizes\":[],\"availability\":[\"AVAILABLE\"],\"admissionDateRanges\":[]},\"resetSearch\":false,\"reference\":null,\"questions\":[\"Which stone type would you like?\",\"What stone size do you prefer?\"],\"reason\":\"A narrowing condition is required.\"}";
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public StoneModelResponseEntity invoke(StoneModelInvocationEntity invocation) {
        return invoke(invocation, client(Duration.between(Instant.now(), invocation.getDeadline())));
    }
    public StoneModelResponseEntity invoke(StoneModelInvocationEntity invocation, RestClient.Builder builder) {
        ChatResponse response = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(invocation.getEndpoint()).restClientBuilder(builder).build())
                .options(OllamaChatOptions.builder().model(MODEL).temperature(0.0)
                        .numCtx(invocation.getContextTokens()).numPredict(invocation.getOutputTokens())
                        .format(invocation.getSchema()).build())
                .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build())).build()
                .call(prompt(invocation));
        int input = response.getMetadata().getUsage().getPromptTokens();
        int output = response.getMetadata().getUsage().getCompletionTokens();
        if (!MODEL.equals(response.getMetadata().getModel()) || input <= 0 || output <= 0 || output > invocation.getOutputTokens()
                || (long) input + output > invocation.getContextTokens()) throw new StoneChatUnavailableException();
        return new StoneModelResponseEntity(response.getResult().getOutput().getText(), input, output);
    }
    public static Prompt prompt(StoneModelInvocationEntity invocation) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(invocation.getSystem()));
        if (invocation.getIncludeExactSearchExample()) {
            messages.add(new UserMessage("Show all available stones."));
            messages.add(new AssistantMessage(CLARIFICATION));
        }
        for (var turn : invocation.getHistory()) {
            messages.add(new UserMessage(turn.getMessage()));
            messages.add(new AssistantMessage(turn.getText()));
        }
        messages.add(new UserMessage(invocation.getMessage()));
        return new Prompt(messages);
    }
    public static int promptUpperBound(StoneModelInvocationEntity invocation) {
        return 32 + prompt(invocation).getInstructions().stream()
                .mapToInt(message -> message.getText().getBytes(StandardCharsets.UTF_8).length + 32).sum();
    }
    public void probe(String endpoint) {
        RestClient restClient = client(Duration.ofSeconds(5)).baseUrl(endpoint).build();
        JsonNode tags = jsonMapper.readTree(restClient.get().uri("/api/tags").retrieve().body(String.class));
        boolean installed = false;
        for (JsonNode model : tags.path("models")) {
            installed |= MODEL.equals(model.path("name").asString()) && DIGEST.equals(model.path("digest").asString());
        }
        JsonNode profile = jsonMapper.readTree(restClient.post().uri("/api/show").body(Map.of("model", MODEL))
                .retrieve().body(String.class));
        try {
            if (!installed || !"gpt2".equals(profile.at("/model_info/tokenizer.ggml.model").asString())
                    || !"qwen2".equals(profile.at("/model_info/tokenizer.ggml.pre").asString())
                    || profile.at("/model_info/qwen2.context_length").asInt() != 32768
                    || !TEMPLATE.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(profile.path("template").asString().getBytes(StandardCharsets.UTF_8)))))
                throw new StoneChatUnavailableException();
        } catch (java.security.NoSuchAlgorithmException exception) { throw new StoneChatUnavailableException(exception); }
    }
    private RestClient.Builder client(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new StoneChatUnavailableException();
        JdkClientHttpRequestFactory jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory(httpClient);
        jdkClientHttpRequestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(jdkClientHttpRequestFactory);
    }
}
