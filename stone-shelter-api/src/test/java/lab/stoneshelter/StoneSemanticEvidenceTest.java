package lab.stoneshelter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lab.stoneshelter.criteria.StoneSemanticWish;
import lab.stoneshelter.entities.ModelTokenReservationEntity;
import lab.stoneshelter.entities.StoneEntity;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.entities.StoneSemanticCandidateEntity;
import lab.stoneshelter.entities.StoneSemanticRetrievalResultEntity;
import lab.stoneshelter.entities.StoneModelInvocationEntity;
import lab.stoneshelter.entities.StoneModelResponseEntity;
import lab.stoneshelter.entities.StoneSemanticSearchExecutionEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.enums.StoneSemanticEvidenceVerdict;
import lab.stoneshelter.enums.StoneSemanticSupportSource;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper;
import lab.stoneshelter.repositories.OllamaChatRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.ModelInvocationSettings;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.OllamaSemanticEvidenceProvider;
import lab.stoneshelter.services.StoneCardSearchSettings;
import lab.stoneshelter.services.StoneExactSearchSettings;
import lab.stoneshelter.services.StoneSemanticEvidenceProvider;
import lab.stoneshelter.services.StoneSemanticEvidenceService;
import lab.stoneshelter.services.StoneSemanticEvidenceValidationService;
import lab.stoneshelter.services.StoneSemanticSearchSettings;
import lab.stoneshelter.services.StoneStructuredModelService;
import lab.stoneshelter.shared.StoneSemanticEvidenceCandidateRequest;
import lab.stoneshelter.shared.StoneSemanticEvidenceRequest;
import lab.stoneshelter.shared.StoneSemanticEvidenceSourceRequest;
import lab.stoneshelter.shared.StoneSemanticWishRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StoneSemanticEvidenceTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<StoneSemanticWishRequest> wishes = List.of(new StoneSemanticWishRequest("use", "shelf"));

    @Test
    void validatesAllPairsAndRejectsInventedBorrowedDuplicateMissingAndTrailingEvidence() {
        StoneSemanticEvidenceRequest request = request(List.of(source("card:1", StoneSemanticSupportSource.CARD, "Suitable for a shelf.")));
        var validation = new StoneSemanticEvidenceValidationService();
        assertThat(validation.validate(good(), request).judgements().getFirst().sourceId()).isEqualTo("card:1");
        for (String invalid : List.of("{}", "{\"judgements\":[]}", good() + " {}", good().replace("s0", "url"),
                good().replace("s0", "s1"), good().replace("use", "invented"), good().replace("Suitable for a shelf.", "borrowed document quote"),
                good().replace("SUPPORTED", "UNKNOWN"), good().replace("\"key\":\"use\"", "\"key\":\"use\",\"key\":\"use\""),
                "{\"judgements\":[" + jsonMapper.readTree(good()).at("/judgements/0") + "," + jsonMapper.readTree(good()).at("/judgements/0") + "]}")) {
            assertThatThrownBy(() -> validation.validate(invalid, request)).isInstanceOf(InvalidStoneSemanticSearchOutputException.class);
        }
    }

    @Test
    void packsSeparateAuthoritiesAndSharesRepairCounterAndContentSensitiveCache() {
        AtomicInteger calls = new AtomicInteger();
        StoneSemanticEvidenceProvider provider = (request, repair) -> {
            int number = calls.incrementAndGet();
            assertThat(request.sources()).allSatisfy(source -> assertThat(source.scope()).isEqualTo(request.sources().getFirst().scope()));
            return new StoneModelResponseEntity(number == 1 ? "{}" : unknown(request), 10, 5);
        };
        var service = service(provider, new MockEnvironment());
        var execution = execution();
        var card = new StoneSemanticEvidenceCandidateRequest(1, StoneType.BASALT, "No individual shelf information.",
                List.of(source("document:1", StoneSemanticSupportSource.TYPE, "General basalt information.")));
        var response = service.checkCandidates(List.of(card), wishes, execution, false);
        assertThat(response.judgements()).hasSize(2).allSatisfy(judgement -> assertThat(judgement.verdict()).isEqualTo(StoneSemanticEvidenceVerdict.UNKNOWN));
        assertThat(calls.get()).isEqualTo(3);
        assertThat(execution.getEvidenceCalls()).isEqualTo(3);
        service.checkCandidates(List.of(card), wishes, execution, false);
        assertThat(calls.get()).isEqualTo(3);
        var changed = new StoneSemanticEvidenceCandidateRequest(1, StoneType.BASALT, "Changed individual facts.", card.sources());
        service.checkCandidates(List.of(changed), wishes, execution, false);
        assertThat(calls.get()).isEqualTo(4);
        assertThat(execution.getEvidenceCalls()).isEqualTo(4);
    }

    @Test
    void sourceScopeAndWholeTurnCapCannotYieldUncheckedSuccess() {
        AtomicInteger calls = new AtomicInteger();
        var service = service((request, repair) -> { calls.incrementAndGet(); return new StoneModelResponseEntity("{}", 1, 1); },
                new MockEnvironment().withProperty("STONE_SEMANTIC_SEARCH_MAX_EVIDENCE_CALLS", "1"));
        var execution = execution();
        assertThatThrownBy(() -> service.checkSources(List.of(source("card:1", StoneSemanticSupportSource.CARD, "Some facts.")), wishes, execution))
                .isInstanceOf(StoneSemanticSearchLimitExceededException.class);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(execution.getEvidenceCache()).isEmpty();
        assertThat(execution.getEvidenceCalls()).isEqualTo(1);
        var wrongType = new StoneSemanticEvidenceSourceRequest("wrong", StoneSemanticSupportSource.TYPE, StoneType.GRANITE, "Granite shelf support.");
        var candidate = new StoneSemanticEvidenceCandidateRequest(1, StoneType.BASALT, "Current card.", List.of(wrongType));
        assertThatThrownBy(() -> service.checkCandidates(List.of(candidate), wishes, execution(), false)).isInstanceOf(InvalidStoneSemanticSearchOutputException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void currentCardVetoDominatesTypeSupportAndOtherMatchedWishes() {
        StoneSemanticEvidenceProvider provider = (request, repair) -> {
            var root = jsonMapper.createObjectNode(); var rows = root.putArray("judgements");
            for (int index = 0; index < request.sources().size(); index++) for (var wish : request.wishes()) {
                var source = request.sources().get(index);
                String verdict = "UNKNOWN", quote = null;
                if (wish.key().equals("use") && source.scope() == StoneSemanticSupportSource.TYPE) {
                    verdict = "SUPPORTED"; quote = "Basalt suits a shelf.";
                } else if (wish.key().equals("use") && source.id().equals("card:2")) {
                    verdict = "SUPPORTED"; quote = "Suits a shelf.";
                } else if (wish.key().equals("use") && source.id().equals("card:3")) {
                    verdict = "CONTRADICTED"; quote = "must not be placed on a shelf.";
                } else if (wish.key().equals("color") && source.id().equals("card:3")) {
                    verdict = "SUPPORTED"; quote = "red";
                }
                var row = rows.addObject().put("key", wish.key()).put("sourceId", "s" + index).put("verdict", verdict);
                if (quote == null) row.putNull("quote"); else row.put("quote", quote);
            }
            return new StoneModelResponseEntity(root.toString(), 20, 10);
        };
        var result = service(provider, new MockEnvironment()).evaluateCandidates(List.of(candidate(1, null),
                candidate(2, "Suits a shelf."), candidate(3, "This red stone must not be placed on a shelf.")),
                List.of(new StoneSemanticWish("use", "shelf"), new StoneSemanticWish("color", "red")), execution(), false);
        assertThat(new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper().toDto(candidate(1, null)).card())
                .contains("Name:", "Type:", "Size:").doesNotContain("Biography:", "none");
        assertThat(result.get(0).getCardSupported()).isFalse();
        assertThat(result.get(0).getMatchedWishes()).extracting(StoneSemanticWish::key).containsExactly("use");
        assertThat(result.get(0).getUnmatchedWishes()).extracting(StoneSemanticWish::key).containsExactly("color");
        assertThat(result.get(0).getSupportingDocumentScore()).isEqualTo(0.9);
        assertThat(result.get(1).getCardSupported()).isTrue();
        assertThat(result.get(1).getContradicted()).isFalse();
        assertThat(result.get(2).getContradicted()).isTrue();
        assertThat(result.get(2).getMatchedWishes()).extracting(StoneSemanticWish::key).contains("color");
    }

    private StoneSemanticCandidateEntity candidate(long id, String biography) {
        StoneEntity stone = new StoneEntity(); stone.setId(id); stone.setName("Candidate " + id);
        stone.setStoneType(StoneType.BASALT); stone.setStoneSize(StoneSize.SMALL); stone.setBiography(biography);
        StoneDocumentFragmentEntity fragment = new StoneDocumentFragmentEntity();
        fragment.setDocumentKey(StoneDocumentIndexEntity.DOCUMENT_KEY); fragment.setSourceVersion("a".repeat(64));
        fragment.setFragmentOrder(0); fragment.setStoneType(StoneType.BASALT); fragment.setText("Basalt suits a shelf.");
        StoneSemanticCandidateEntity candidate = new StoneSemanticCandidateEntity(); candidate.setStone(stone);
        candidate.setPassages(List.of(new StoneSemanticRetrievalResultEntity.Passage(fragment, 0.9)));
        return candidate;
    }

    @Test
    void processesCompleteSegmentsAndFailsForAnIndivisibleMandatoryInput() {
        AtomicInteger processed = new AtomicInteger();
        var service = service((request, repair) -> { processed.addAndGet(request.sources().size()); return new StoneModelResponseEntity(unknown(request), 1, 1); }, new MockEnvironment());
        String text = ("This complete sentence contains unrelated background facts. ").repeat(170);
        var execution = execution();
        var response = service.checkSources(List.of(source("large", StoneSemanticSupportSource.TYPE, text)), wishes, execution);
        assertThat(processed.get()).isGreaterThan(1);
        assertThat(response.judgements()).hasSize(1);
        assertThat(response.judgements().getFirst().verdict()).isEqualTo(StoneSemanticEvidenceVerdict.UNKNOWN);
        assertThat(execution.getEvidenceCache()).hasSize(1);
        int previous = processed.get();
        assertThatThrownBy(() -> service.checkSources(List.of(source("indivisible", StoneSemanticSupportSource.TYPE, "\u9fff".repeat(10000))), wishes, execution()))
                .isInstanceOf(StoneSemanticSearchLimitExceededException.class);
        assertThat(processed.get()).isEqualTo(previous);
    }

    @Test
    void existingSdkWireUsesTheNewSchemaWithoutExactExampleAndSettlesReportedUsage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://qualification.invalid/api/chat"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(request -> {
                    var body = jsonMapper.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    var input = jsonMapper.readTree(body.at("/messages/1/content").asString());
                    assertThat(input.at("/wishes/0/key").asString()).isEqualTo("use");
                    assertThat(input.at("/wishes/0/text").asString()).isEqualTo("shelf");
                    assertThat(input.at("/source/text").asString()).isEqualTo("Suitable for a shelf.");
                    assertThat(input.at("/source/scope").asString()).isEqualTo("TYPE");
                    assertThat(input.at("/source/stoneType").isMissingNode()).isTrue();
                })
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andExpect(jsonPath("$.options.num_predict").value(512))
                .andExpect(jsonPath("$.format.properties.judgements.items.properties.sourceId.enum[0]").value("s0"))
                .andRespond(withSuccess(jsonMapper.writeValueAsString(Map.of("model", "qwen2.5:3b", "done", true,
                        "message", Map.of("role", "assistant", "content", good()), "prompt_eval_count", 11, "eval_count", 6)), MediaType.APPLICATION_JSON));
        ModelTokenBudgetTransactionService ledger = ledger();
        ModelInvocationAdmissionService admission = admission(ledger);
        OllamaChatRepository repository = new OllamaChatRepository() {
            @Override public void probe(String endpoint) {}
            @Override public StoneModelResponseEntity invoke(StoneModelInvocationEntity invocation) { return super.invoke(invocation, builder); }
        };
        var guard = guard(repository, admission);
        var provider = new OllamaSemanticEvidenceProvider(guard);
        var settings = new StoneSemanticSearchSettings(new MockEnvironment(), new StoneCardSearchSettings(new MockEnvironment()));
        var service = new StoneSemanticEvidenceService(provider, new StoneSemanticEvidenceValidationService(),
                new StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper(), provider, settings, Clock.systemUTC(), new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper());
        assertThat(service.checkSources(List.of(source("real-document-id", StoneSemanticSupportSource.TYPE, "Suitable for a shelf.")), wishes, execution())
                .judgements().getFirst().sourceId()).isEqualTo("real-document-id");
        verify(ledger).reserve(any(), any(), eq(8192L), eq(100000L));
        verify(ledger).settle(any(), eq(17L));
        server.verify();
    }

    @Test
    void timedOutWorkerRetainsSharedPermitUntilItsActualExit() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Thread> actualWorker = new AtomicReference<>();
        OllamaChatRepository repository = mock(OllamaChatRepository.class);
        when(repository.invoke(any())).thenAnswer(call -> {
            actualWorker.set(Thread.currentThread()); entered.countDown();
            boolean done = false;
            while (!done) {
                try { done = release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { /* controlled uncooperative invocation */ }
            }
            return new StoneModelResponseEntity("{}", 1, 1);
        });
        ModelInvocationAdmissionService admission = admission(ledger());
        var guard = guard(repository, admission);
        try {
            assertThatThrownBy(() -> guard.invoke("Classify.", "Data.", Map.of(), Instant.now().plusMillis(200), 512))
                    .isInstanceOf(StoneChatUnavailableException.class);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> admission.acquire(ModelAdmissionMode.CUSTOMER, 8192, Instant.now().plusSeconds(1)))
                    .isInstanceOf(ModelCapacityExceededException.class);
        } finally {
            release.countDown();
            if (actualWorker.get() != null) actualWorker.get().join(2000);
        }
        assertThat(actualWorker.get().isAlive()).isFalse();
        try (var lease = admission.acquire(ModelAdmissionMode.CUSTOMER, 8192, Instant.now().plusSeconds(1))) { lease.settle(0); }
    }

    private StoneSemanticEvidenceService service(StoneSemanticEvidenceProvider provider, MockEnvironment environment) {
        var packing = new OllamaSemanticEvidenceProvider(guard(mock(OllamaChatRepository.class), admission(ledger())));
        return new StoneSemanticEvidenceService(provider, new StoneSemanticEvidenceValidationService(),
                new StoneSemanticEvidenceResultEntityToStoneSemanticEvidenceResponseMapper(), packing,
                new StoneSemanticSearchSettings(environment, new StoneCardSearchSettings(environment)), Clock.systemUTC(),
                new StoneSemanticCandidateEntityToStoneSemanticEvidenceCandidateRequestMapper());
    }

    private StoneStructuredModelService guard(OllamaChatRepository repository, ModelInvocationAdmissionService admission) {
        return new StoneStructuredModelService(repository, admission, new StoneExactSearchSettings(
                new MockEnvironment().withProperty("CHAT_MODEL_OLLAMA_BASE_URL", "http://qualification.invalid")), Clock.systemUTC());
    }

    private ModelInvocationAdmissionService admission(ModelTokenBudgetTransactionService ledger) {
        return new ModelInvocationAdmissionService(ledger, new ModelInvocationSettings(new MockEnvironment()), Clock.systemUTC());
    }

    private ModelTokenBudgetTransactionService ledger() {
        ModelTokenBudgetTransactionService ledger = mock(ModelTokenBudgetTransactionService.class);
        when(ledger.reserve(any(), any(), anyLong(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), call.getArgument(1), call.getArgument(2), null));
        when(ledger.settle(any(), anyLong())).thenAnswer(call -> new ModelTokenReservationEntity(call.getArgument(0), LocalDate.now(Clock.systemUTC()), 8192, call.getArgument(1)));
        return ledger;
    }

    private StoneSemanticEvidenceRequest request(List<StoneSemanticEvidenceSourceRequest> sources) {
        return new StoneSemanticEvidenceRequest(wishes, sources, Instant.now().plusSeconds(30));
    }

    private StoneSemanticEvidenceSourceRequest source(String id, StoneSemanticSupportSource scope, String text) {
        return new StoneSemanticEvidenceSourceRequest(id, scope, StoneType.BASALT, text);
    }

    private StoneSemanticSearchExecutionEntity execution() {
        StoneSemanticSearchExecutionEntity execution = new StoneSemanticSearchExecutionEntity();
        execution.setDeadline(Instant.now().plusSeconds(120)); execution.setModelDeadline(execution.getDeadline()); return execution;
    }

    private String good() { return "{\"judgements\":[{\"key\":\"use\",\"sourceId\":\"s0\",\"verdict\":\"SUPPORTED\",\"quote\":\"Suitable for a shelf.\"}]}"; }

    private String unknown(StoneSemanticEvidenceRequest request) {
        var root = jsonMapper.createObjectNode(); var rows = root.putArray("judgements");
        for (int index = 0; index < request.sources().size(); index++) for (var wish : request.wishes()) {
            rows.addObject().put("key", wish.key()).put("sourceId", "s" + index).put("verdict", "UNKNOWN").putNull("quote");
        }
        return root.toString();
    }
}
