package lab.stoneshelter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.services.OllamaExactSearchProvider;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import java.util.List;
import java.util.Map;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import lab.stoneshelter.services.StoneExactSearchSettings;
import org.springframework.mock.env.MockEnvironment;

import static lab.stoneshelter.services.StoneExactSearchSettings.SCHEMA;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class StoneExactSearchQualificationTest {
    private static final int CONTEXT = 8192;
    private static final int OUTPUT = 512;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void installedModelProducesBasicExactAndClarificationDecisionsWithActualUsage() {
        for (String request : List.of("Find small basalt stones.", "Show all available stones.")) {
            ChatResponse response = model(endpoint(), CONTEXT, OUTPUT, realClient(), settings().schemaFor(request, false)).call(prompt(request));
            JsonNode decision = jsonMapper.readTree(response.getResult().getOutput().getText());
            System.out.println("QUALIFICATION complete-decision: " + decision);
            if (request.startsWith("Show") && !"CLARIFY".equals(decision.path("action").asString())) {
                assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
                System.out.println("QUALIFICATION repair-required: input=" + response.getMetadata().getUsage().getPromptTokens()
                        + ", output=" + response.getMetadata().getUsage().getCompletionTokens());
                response = model(endpoint(), CONTEXT, OUTPUT, realClient(), settings().schemaFor(request, false))
                        .call(prompt(request, "Use CLARIFY with two specific type, size or date questions; invent no criteria."));
                decision = jsonMapper.readTree(response.getResult().getOutput().getText());
                System.out.println("QUALIFICATION repaired-decision: " + decision);
            }
            System.out.println("QUALIFICATION action=" + decision.path("action").asString()
                    + ", questions=" + decision.path("questions").size());
            assertThat(decision.propertyNames()).containsExactlyInAnyOrder("action", "criteria", "resetSearch", "reference", "questions", "reason");
            if (request.startsWith("Find")) {
                assertThat(decision.get("action").asString()).isEqualTo("SEARCH");
                assertThat(decision.at("/criteria/stoneTypes/0").asString()).isEqualTo("BASALT");
                assertThat(decision.at("/criteria/stoneSizes/0").asString()).isEqualTo("SMALL");
            } else {
                assertThat(decision.get("action").asString()).isEqualTo("CLARIFY");
                assertThat(decision.get("questions").size()).isGreaterThanOrEqualTo(2);
                assertThat(decision.at("/criteria/stoneTypes").size()).isZero();
                assertThat(decision.at("/criteria/stoneSizes").size()).isZero();
                assertThat(decision.at("/criteria/admissionDateRanges").size()).isZero();
            }
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
            assertThat(response.getMetadata().getUsage().getCompletionTokens()).isBetween(1, OUTPUT);
            assertThat(response.getMetadata().getUsage().getTotalTokens()).isLessThanOrEqualTo(CONTEXT);
            System.out.println("QUALIFICATION basic: input=" + response.getMetadata().getUsage().getPromptTokens()
                    + ", output=" + response.getMetadata().getUsage().getCompletionTokens());
        }
    }

    @Test
    void malformedTruncatedOutputRetainsReportedUsageForRepairAccounting() {
        ChatResponse response = model(endpoint(), CONTEXT, 1, realClient()).call(prompt("Find small basalt stones."));
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
        assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(1);
        assertThatThrownBy(() -> jsonMapper.readTree(response.getResult().getOutput().getText())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void failedSdkInvocationHasNoHiddenRetry() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("http://qualification.invalid/api/chat"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[3].content").value("Find small basalt stones."))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.num_ctx").value(CONTEXT))
                .andExpect(jsonPath("$.options.num_predict").value(OUTPUT))
                .andExpect(jsonPath("$.format.properties.action.enum[0]").value("CLARIFY"))
                .andRespond(withServerError());
        StoneModelInvocationEntity invocation = new StoneModelInvocationEntity();
        invocation.setEndpoint("http://qualification.invalid");
        invocation.setSystem(settings().systemInstructions(LocalDate.of(2026, 10, 12), null));
        invocation.setMessage("Find small basalt stones."); invocation.setHistory(List.of());
        invocation.setContextTokens(CONTEXT); invocation.setOutputTokens(OUTPUT);
        invocation.setSchema(settings().schemaFor(invocation.getMessage(), false));
        invocation.setDeadline(Instant.now().plusSeconds(50));
        assertThatThrownBy(() -> new OllamaChatRepository().invoke(invocation, restClientBuilder))
                .isInstanceOf(RuntimeException.class);
        server.verify();
    }

    @Test
    void providerTrimsOnlyWholePairsPreservingCurrentRequestAndUtcInstructions() {
        OllamaChatRepository repository = mock(OllamaChatRepository.class);
        when(repository.invoke(any())).thenReturn(new StoneModelResponseEntity("{}", 10, 2));
        List<StoneChatTurnEntity> history = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            StoneChatTurnEntity pair = new StoneChatTurnEntity();
            pair.setMessage("x".repeat(2000)); pair.setText("Earlier answer"); history.add(pair);
        }
        StoneChatTurnEntity current = new StoneChatTurnEntity(); current.setMessage("Find small basalt stones.");
        Instant now = Instant.parse("2026-10-12T22:00:00Z");
        OllamaExactSearchProvider provider = new OllamaExactSearchProvider(repository, settings(), Clock.fixed(now, ZoneOffset.UTC));
        ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
        when(ledger.reserve(any(), any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), LocalDate.of(2026, 10, 12), CONTEXT, call.getArgument(1)));
        provider.configureAdmission(new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), Clock.fixed(now, ZoneOffset.UTC)));
        provider.invoke(current, history, now.plusSeconds(120), null);
        ArgumentCaptor<StoneModelInvocationEntity> captured = ArgumentCaptor.forClass(StoneModelInvocationEntity.class);
        verify(repository).invoke(captured.capture());
        assertThat(captured.getValue().getMessage()).isEqualTo(current.getMessage());
        assertThat(captured.getValue().getSystem()).contains("today is 2026-10-12", "yesterday 2026-10-11");
        assertThat(captured.getValue().getDeadline()).isEqualTo(now.plusSeconds(50));
        assertThat(captured.getValue().getHistory()).hasSizeLessThan(history.size());
        assertThat(history).hasSize(10);
        assertThat(OllamaChatRepository.promptUpperBound(captured.getValue()) + OUTPUT).isLessThanOrEqualTo(CONTEXT);
    }

    @Test
    void installedProviderInterfaceReservesAndSettlesActualSdkUsage() {
        Clock clock = Clock.systemUTC(); ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
        when(ledger.reserve(any(), any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), LocalDate.now(clock), CONTEXT, call.getArgument(1)));
        var provider = new OllamaExactSearchProvider(new OllamaChatRepository(), settings(), clock);
        provider.configureAdmission(new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), clock));
        StoneChatTurnEntity turn = new StoneChatTurnEntity(); turn.setMessage("Find small basalt stones.");
        StoneModelResponseEntity response = provider.invoke(turn, List.of(), clock.instant().plusSeconds(120), null);
        JsonNode decision = jsonMapper.readTree(response.text());
        assertThat(decision.path("action").asString()).isEqualTo("SEARCH");
        assertThat(decision.at("/criteria/stoneTypes/0").asString()).isEqualTo("BASALT");
        assertThat(response.inputTokens()).isPositive(); assertThat(response.outputTokens()).isPositive();
        verify(ledger).reserve(any(), any(), org.mockito.ArgumentMatchers.eq((long) CONTEXT), org.mockito.ArgumentMatchers.eq(100000L));
        verify(ledger).settle(any(), org.mockito.ArgumentMatchers.eq((long) response.inputTokens() + response.outputTokens()));
    }

    @Test
    void productionJdkTransportBoundsHeadersAndBodyAndDoesNotRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1); var requests = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/api/chat", exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("{\"model\":\"qwen2.5:3b\",\"message\":".getBytes(StandardCharsets.UTF_8)); exchange.getResponseBody().flush();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        }); server.start();
        try {
            StoneModelInvocationEntity invocation = new StoneModelInvocationEntity();
            invocation.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort()); invocation.setHistory(List.of());
            invocation.setSystem(settings().systemInstructions(LocalDate.of(2026, 10, 12), null)); invocation.setMessage("Find small basalt stones.");
            invocation.setSchema(settings().schemaFor(invocation.getMessage(), false)); invocation.setContextTokens(CONTEXT); invocation.setOutputTokens(OUTPUT);
            OllamaChatRepository repository = new OllamaChatRepository();
            invocation.setDeadline(Instant.now().plusSeconds(2)); long started = System.nanoTime();
            assertThatThrownBy(() -> repository.invoke(invocation)).isInstanceOf(RuntimeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
            assertThat(requests.get()).isEqualTo(1);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test
    void existingMaximumRequestAndSystemFitTheAcceptedContextWithoutTruncation() throws Exception {
        String prefix = "Find small basalt stones. The following quoted note is irrelevant to the search: ";
        String request = prefix + "\"" + "\u9fff".repeat(1998 - prefix.length()) + "\"";
        assertThat(request.length()).isEqualTo(2000);
        var constraint = StoneChatMessageRequest.class.getMethod("message")
                .getAnnotation(jakarta.validation.constraints.Size.class);
        assertThat(constraint.max()).isEqualTo(request.length());
        JsonNode profile = jsonMapper.readTree(realClient().baseUrl(endpoint()).build().post().uri("/api/show")
                .body(Map.of("model", "qwen2.5:3b")).retrieve().body(String.class));
        assertThat(profile.at("/model_info/tokenizer.ggml.model").asString()).isEqualTo("gpt2");
        assertThat(profile.at("/model_info/tokenizer.ggml.pre").asString()).isEqualTo("qwen2");
        assertThat(profile.at("/model_info/qwen2.context_length").asInt()).isGreaterThanOrEqualTo(CONTEXT);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(profile.path("template").asString().getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(StoneExactSearchSettings.QUALIFIED_TEMPLATE_SHA256);
        // Qwen2 byte-level BPE merges byte tokens; it cannot exceed the UTF-8
        // byte count. Allow 32 tokens per ChatML message plus the assistant header.
        // Bound ALL permitted 2000-UTF-16-unit inputs, not just this fixture's count.
        int fixedPromptBound = prompt("").getInstructions().stream()
                .mapToInt(message -> message.getText().getBytes(StandardCharsets.UTF_8).length + 32).sum() + 32;
        int inputTokens = fixedPromptBound + constraint.max() * 3 + StoneExactSearchSettings.REPAIR_PROMPT_ALLOWANCE;
        System.out.println("QUALIFICATION maximum-request: UTF-16=" + request.length()
                + ", UTF-8=" + request.getBytes(StandardCharsets.UTF_8).length + ", inputUpperBound=" + inputTokens
                + ", outputReservation=" + OUTPUT + ", acceptedContext=" + CONTEXT);
        assertThat(inputTokens).isGreaterThan(4096);
        assertThat(inputTokens + OUTPUT).as("Complete system/schema/current request plus output must fit; trimming old history cannot fix this input.")
                .isLessThanOrEqualTo(CONTEXT);
    }

    private Prompt prompt(String request) {
        return prompt(request, null);
    }

    private Prompt prompt(String request, String repairReason) {
        return new Prompt(List.of(new SystemMessage(settings().systemInstructions(LocalDate.of(2026, 10, 12), repairReason)),
                new UserMessage("Show all available stones."),
                new AssistantMessage("{\"action\":\"CLARIFY\",\"criteria\":{\"stoneTypes\":[],\"stoneSizes\":[],\"availability\":[\"AVAILABLE\"],\"admissionDateRanges\":[]},\"resetSearch\":false,\"reference\":null,\"questions\":[\"Which stone type would you like?\",\"What stone size do you prefer?\"],\"reason\":\"A narrowing condition is required.\"}"),
                new UserMessage(request)));
    }

    private StoneExactSearchSettings settings() {
        return new StoneExactSearchSettings(new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", endpoint()));
    }

    private RestClient.Builder realClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(50));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private OllamaChatModel model(String endpoint, int context, int output, RestClient.Builder restClientBuilder) {
        return model(endpoint, context, output, restClientBuilder, jsonMapper.readValue(SCHEMA, Map.class));
    }

    private OllamaChatModel model(String endpoint, int context, int output, RestClient.Builder restClientBuilder, Map<String, Object> schema) {
        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(endpoint).restClientBuilder(restClientBuilder).build();
        return OllamaChatModel.builder().ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model("qwen2.5:3b").temperature(0.0).numCtx(context).numPredict(output)
                        .format(schema).build())
                .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build())).build();
    }

    private String endpoint() {
        String endpoint = System.getenv("CHAT_MODEL_OLLAMA_BASE_URL");
        assertThat(endpoint).as("Explicitly supplied authorized Ollama endpoint").isNotBlank();
        return endpoint;
    }
}
