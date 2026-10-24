package lab.stoneshelter;

import java.util.List;
import java.util.UUID;
import lab.stoneshelter.enums.StoneDocumentIndexOutcome;
import lab.stoneshelter.exceptions.StoneDocumentIndexConflictException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentSourceNotFoundException;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import lab.stoneshelter.services.StoneEncyclopediaIndexService;
import lab.stoneshelter.shared.StoneEncyclopediaIndexResponse;
import lab.stoneshelter.shared.StoneEncyclopediaDeleteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneEncyclopediaControllerTest extends IntegrationTest {
    private static final String PATH = "/api/stone-encyclopedia/index";
    @Autowired private RestTestClient client;
    @MockitoBean private StoneEncyclopediaIndexService service;

    @Test
    void creationReplacementAndHistoricalStatusesHaveExactlyThreeWireFields() {
        UUID key = UUID.randomUUID();
        when(service.importIndex(key)).thenReturn(new StoneEncyclopediaIndexResponse("stone-encyclopedia-en", "a".repeat(64), 10, StoneDocumentIndexOutcome.CREATED));
        for (int i = 0; i < 2; i++) client.post().uri(PATH).header("Idempotency-Key", key.toString()).exchange()
                .expectStatus().isCreated().expectBody().jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$.documentKey").isEqualTo("stone-encyclopedia-en").jsonPath("$.indexedFragments").isEqualTo(10)
                .jsonPath("$.outcome").doesNotExist();
        when(service.importIndex(key)).thenReturn(new StoneEncyclopediaIndexResponse("stone-encyclopedia-en", "b".repeat(64), 9, StoneDocumentIndexOutcome.REPLACED));
        client.post().uri(PATH).header("Idempotency-Key", key.toString()).exchange().expectStatus().isOk();
    }

    @Test
    void deletionIsOpenAndIdentifierOnly() {
        when(service.delete(any())).thenReturn(new StoneEncyclopediaDeleteResponse("stone-encyclopedia-en"));
        client.delete().uri(PATH).header("Idempotency-Key", UUID.randomUUID().toString()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$.id").isEqualTo("stone-encyclopedia-en");
    }

    @Test
    void rejectsMissingNonV4AndNonemptyBodiesBeforeServiceAdmission() {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.DELETE)) {
            client.method(method).uri(PATH).exchange().expectStatus().isBadRequest()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            for (String key : List.of("invalid", "00000000-0000-1000-8000-000000000000", "00000000-0000-4000-0000-000000000000")) {
                client.method(method).uri(PATH).header("Idempotency-Key", key).exchange().expectStatus().isBadRequest();
            }
            client.method(method).uri(PATH).header("Idempotency-Key", UUID.randomUUID().toString()).body(" ")
                    .exchange().expectStatus().isBadRequest();
        }
        verifyNoInteractions(service);
    }

    @Test
    void dependencyBusinessAndConflictFailuresAreProblemDetails() {
        UUID key = UUID.randomUUID();
        List<RuntimeException> failures = List.of(new StoneDocumentSourceNotFoundException(),
                new StoneDocumentSourceUnindexableException("invalid source"),
                new StoneDocumentDependencyUnavailableException("dependency unavailable"),
                new StoneDocumentIndexConflictException("This operation is already running."));
        int[] statuses = {404, 422, 503, 409};
        for (int i = 0; i < failures.size(); i++) {
            doThrow(failures.get(i)).when(service).importIndex(key);
            client.post().uri(PATH).header("Idempotency-Key", key.toString()).exchange().expectStatus().isEqualTo(statuses[i])
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.detail").isEqualTo(failures.get(i).getMessage());
        }
        when(service.delete(key)).thenThrow(new StoneDocumentIndexConflictException("The key has expired; use a new key."));
        client.delete().uri(PATH).header("Idempotency-Key", key.toString()).exchange().expectStatus().isEqualTo(409);
    }
}
