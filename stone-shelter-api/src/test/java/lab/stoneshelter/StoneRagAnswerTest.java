package lab.stoneshelter;

import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.entities.StoneModelInvocationEntity;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.entities.StoneRagExecutionEntity;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGenerationRequestToStoneRagGroundingRequestMapper;
import lab.stoneshelter.mappers.dtos.StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.OllamaRagAnswerProvider;
import lab.stoneshelter.services.OllamaRagGroundingProvider;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.services.StoneExactSearchSettings;
import lab.stoneshelter.services.StoneRagAnswerProvider;
import lab.stoneshelter.services.StoneRagAnswerService;
import lab.stoneshelter.services.StoneRagAnswerValidationService;
import lab.stoneshelter.services.StoneRagGroundingProvider;
import lab.stoneshelter.services.StoneRagGroundingService;
import lab.stoneshelter.services.StoneRagSettings;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import lab.stoneshelter.services.StoneStructuredModelService;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import lab.stoneshelter.shared.StoneRagCandidateRequest;
import lab.stoneshelter.shared.StoneRagSourceRequest;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StoneRagAnswerTest {
    private final Clock clock = Clock.systemUTC();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MockEnvironment environment = new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://qualification.invalid");
    private StoneStructuredModelService packing() { return new StoneStructuredModelService(mock(OllamaChatRepository.class), mock(ModelInvocationAdmissionService.class), new StoneExactSearchSettings(environment), clock); }
    private StoneRagGroundingService grounding(StoneRagGroundingProvider provider) {
        return new StoneRagGroundingService(provider, new OllamaRagGroundingProvider(packing()), new StoneRagGenerationRequestToStoneRagGroundingRequestMapper(),
                new StoneRagGroundingResultEntityToStoneRagGroundingResponseMapper(), new StoneSemanticSearchSettings(environment, new StoneCardSearchSettings(environment)), clock);
    }
    private StoneRagAnswerService service(StoneRagAnswerProvider provider, StoneRagGroundingProvider checker) {
        return new StoneRagAnswerService(provider, new StoneRagAnswerValidationService(), grounding(checker),
                new StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper(new StoneRagGeneratedAnswerEntityToStoneRagGeneratedAnswerResponseMapper()),
                new StoneRagSettings(environment, new StoneExactSearchSettings(environment)), clock);
    }
    @Test void strictStructureIdsOrderAndVisibleAttributionCannotBecomeAcceptedProse() {
        var validation = new StoneRagAnswerValidationService(); var request = request(StoneRagMode.SELECTION);
        assertThat(validation.validate(good(), request).stones()).hasSize(2);
        for (String output : List.of("{}", good() + " {}", good().replace("\"id\":1", "\"id\":3"),
                good().replace("Shelf", "According to the encyclopedia"), good().replace("Shelf", "https://source.invalid"),
                good().replace("Shelf", "source-id"), good().replace("\"introduction\":", "\"introduction\":\"duplicate\",\"introduction\":"),
                good().replace("\"id\":1,\"explanation\":\"Shelf; requested color is unconfirmed.\"", "\"id\":1,\"explanation\":\"Alpha\"")))
            assertThatThrownBy(() -> validation.validate(output, request)).isInstanceOf(InvalidStoneRagOutputException.class);
    }
    @Test void groundingFailureGetsOneGeneratorCorrectionAndSharesAllCheckingCalls() {
        AtomicInteger generations = new AtomicInteger(), checks = new AtomicInteger();
        var request = request(StoneRagMode.SELECTION); var execution = execution();
        var service = service((value, repair) -> response(generations.getAndIncrement() == 0 ? good().replace("Shelf", "Bright red and soak") : good()),
                (value, repair) -> { checks.incrementAndGet(); return response("{\"valid\":" + !value.prose().contains("Bright red") + ",\"reason\":\"Checked\"}"); });
        var result = service.generate(request, execution);
        assertThat(result.completion()).isEqualTo(StoneRagCompletionKind.GENERATED); assertThat(result.answer().stones()).hasSize(2);
        assertThat(result.answer().stones().getFirst().explanation()).doesNotContain("Bright red");
        assertThat(generations.get()).isEqualTo(2); assertThat(execution.getGenerationCalls()).isEqualTo(2);
        assertThat(execution.getEvidenceCalls()).isEqualTo(checks.get()).isEqualTo(6);
    }
    @Test void checkerRepairConsumesSharedCapAndCannotApproveMalformedOrIncompleteWork() {
        AtomicInteger calls = new AtomicInteger(); var execution = execution(); execution.setEvidenceCalls(18);
        var checker = grounding((request, repair) -> { calls.incrementAndGet(); return response("{}"); });
        var request = request(StoneRagMode.SELECTION); var draft = new StoneRagAnswerValidationService().validate(good(), request);
        assertThatThrownBy(() -> checker.prepare(request, draft, execution)).isInstanceOf(InvalidStoneRagOutputException.class);
        assertThat(execution.getEvidenceCalls()).isEqualTo(20); assertThat(calls.get()).isEqualTo(2);
        assertThatThrownBy(() -> checker.prepare(request, draft, execution)).isInstanceOf(StoneSemanticSearchLimitExceededException.class);
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void selectionFailureReturnsOnlyTemplateDecisionAndKnowledgeFailureEscapes() {
        var service = service((request, repair) -> { throw new ModelCapacityExceededException(); }, (request, repair) -> response("{\"valid\":true,\"reason\":\"Checked\"}"));
        var result = service.generate(request(StoneRagMode.SELECTION), execution());
        assertThat(result.completion()).isEqualTo(StoneRagCompletionKind.TEMPLATE); assertThat(result.answer()).isNull();
        assertThatThrownBy(() -> service.generate(request(StoneRagMode.KNOWLEDGE), execution())).isInstanceOf(ModelCapacityExceededException.class);
        AtomicInteger calls = new AtomicInteger();
        var invalid = service((request, repair) -> { calls.incrementAndGet(); return response("{}"); }, (request, repair) -> response("{}"));
        var execution = execution(); assertThat(invalid.generate(request(StoneRagMode.SELECTION), execution).completion()).isEqualTo(StoneRagCompletionKind.TEMPLATE);
        assertThat(execution.getGenerationCalls()).isEqualTo(2); assertThat(calls.get()).isEqualTo(2);
    }
    @Test void generationCapAndEarlierDeadlineCannotBeRestartedAndEmptySelectionNeverCallsProvider() {
        environment.withProperty("CHAT_RAG_MAX_GENERATION_CALLS", "1"); AtomicInteger calls = new AtomicInteger();
        var service = service((request, repair) -> { calls.incrementAndGet(); return response("{}"); }, (request, repair) -> response("{}"));
        var execution = execution(); var result = service.prepare(request(StoneRagMode.SELECTION), execution);
        assertThat(result.completion()).isEqualTo(StoneRagCompletionKind.TEMPLATE); assertThat(result.failure()).isInstanceOf(StoneRagAnswerLimitExceededException.class);
        assertThat(calls.get()).isEqualTo(1); assertThat(execution.getGenerationCalls()).isEqualTo(1);
        var empty = request(StoneRagMode.SELECTION); empty.setCandidates(List.of());
        assertThatThrownBy(() -> service.generate(empty, execution())).isInstanceOf(IllegalArgumentException.class); assertThat(calls.get()).isEqualTo(1);
        var expired = execution(); expired.setModelDeadline(clock.instant().minusSeconds(1));
        assertThat(service.prepare(request(StoneRagMode.SELECTION), expired).answer()).isNull(); assertThat(calls.get()).isEqualTo(1);
    }
    @Test void completeSourceSegmentsAreCheckedAndIndivisibleContextCannotBeTruncated() {
        String text = "Basalt fits a shelf. ".repeat(900); AtomicInteger processed = new AtomicInteger();
        var request = request(StoneRagMode.SELECTION); request.setCandidates(List.of(request.getCandidates().getFirst()));
        request.setSources(List.of(new StoneRagSourceRequest("large", StoneSemanticSupportSource.TYPE, StoneType.BASALT, text)));
        var checker = grounding((value, repair) -> { processed.addAndGet(value.facts().getSources().stream().mapToInt(source -> source.text().length()).sum()); return response("{\"valid\":true,\"reason\":\"Checked\"}"); });
        var draft = new StoneRagAnswerValidationService().validate("{\"introduction\":\"Options.\",\"stones\":[{\"id\":1,\"explanation\":\"Fits the shelf; color unconfirmed.\"}],\"knowledge\":null}", request);
        assertThat(checker.prepare(request, draft, execution()).valid()).isTrue(); assertThat(processed.get()).isEqualTo(text.length() * 2);
        request.setSources(List.of(new StoneRagSourceRequest("large", StoneSemanticSupportSource.TYPE, StoneType.BASALT, "x".repeat(12000))));
        int before = processed.get(); assertThatThrownBy(() -> checker.prepare(request, draft, execution())).isInstanceOf(StoneRagAnswerLimitExceededException.class);
        assertThat(processed.get()).isEqualTo(before);
    }
    @Test void existingSdkWireUses1024GenerationAnd512ChecksWithoutChangingTotalReservation() {
        RestClient.Builder builder = RestClient.builder(); MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://qualification.invalid/api/chat")).andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andExpect(jsonPath("$.options.num_predict").value(1024)).andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(request -> {
                    var body = jsonMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.at("/format/properties/knowledge/type").asString()).isEqualTo("null");
                    assertThat(body.at("/messages/1/content").asString()).isEqualTo("Find stones for a shelf.");
                }).andRespond(withSuccess(jsonMapper.writeValueAsString(Map.of("model", "qwen2.5:3b", "done", true, "message", Map.of("role", "assistant", "content", good()), "prompt_eval_count", 10, "eval_count", 25)), MediaType.APPLICATION_JSON));
        var ledger = mock(ModelTokenBudgetTransactionService.class);
        when(ledger.reserve(any(), any(), anyLong(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), LocalDate.now(ZoneOffset.UTC), 8192, call.getArgument(1)));
        var admission = new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), clock);
        var repository = new OllamaChatRepository() {
            @Override public void probe(String endpoint) {}
            @Override public StoneModelResponseEntity invoke(StoneModelInvocationEntity invocation) { return super.invoke(invocation, builder); }
        };
        var guard = new StoneStructuredModelService(repository, admission, new StoneExactSearchSettings(environment), clock);
        var provider = new OllamaRagAnswerProvider(guard, new StoneRagSettings(environment, new StoneExactSearchSettings(environment)));
        assertThat(provider.invoke(request(StoneRagMode.SELECTION), null).outputTokens()).isEqualTo(25);
        org.mockito.Mockito.verify(ledger).reserve(any(), any(), org.mockito.ArgumentMatchers.eq(8192L), org.mockito.ArgumentMatchers.eq(100000L));
        org.mockito.Mockito.verify(ledger).settle(any(), org.mockito.ArgumentMatchers.eq(35L)); server.verify();
        assertThat(new OllamaRagGroundingProvider(guard).schema().keySet()).contains("type", "properties", "required");
    }
    private StoneRagGenerationRequest request(StoneRagMode mode) {
        StoneRagGenerationRequest request = new StoneRagGenerationRequest(); request.setMode(mode); request.setQuestion("Find stones for a shelf."); request.setDeadline(clock.instant().plusSeconds(115));
        if (mode == StoneRagMode.SELECTION) request.setCandidates(List.of(candidate(1, "Alpha"), candidate(2, "Beta")));
        request.setSources(List.of(new StoneRagSourceRequest("source-id", StoneSemanticSupportSource.TYPE, StoneType.BASALT, "Basalt fits a shelf."))); return request;
    }
    private StoneRagCandidateRequest candidate(long id, String name) { var candidate = new StoneRagCandidateRequest(); candidate.setId(id); candidate.setName(name); candidate.setStoneType(StoneType.BASALT); candidate.setStoneSize(StoneSize.SMALL); candidate.setBiography("Shelf use; keep dry."); candidate.setMatched(List.of(new StoneSemanticWishRequest("use", "shelf"))); candidate.setUnmatched(List.of(new StoneSemanticWishRequest("color", "red"))); return candidate; }
    private StoneRagExecutionEntity execution() { var execution = new StoneRagExecutionEntity(); execution.setDeadline(clock.instant().plusSeconds(120)); execution.setModelDeadline(execution.getDeadline().minusSeconds(5)); return execution; }
    private String good() { return "{\"introduction\":\"Checked options.\",\"stones\":[{\"id\":1,\"explanation\":\"Shelf; requested color is unconfirmed.\"},{\"id\":2,\"explanation\":\"Shelf; requested color is unconfirmed.\"}],\"knowledge\":null}"; }
    private StoneModelResponseEntity response(String output) { return new StoneModelResponseEntity(output, 20, 20); }
}
