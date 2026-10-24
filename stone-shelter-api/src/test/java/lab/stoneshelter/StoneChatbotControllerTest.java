package lab.stoneshelter;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lab.stoneshelter.services.StoneRagConversationService;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.controllers.StoneChatbotController;
import lab.stoneshelter.controllers.StoneChatHistoryController;
import lab.stoneshelter.controllers.StoneChatBindingInterceptor;
import lab.stoneshelter.controllers.StoneChatWebConfiguration;
import lab.stoneshelter.exceptions.StoneChatConversationBusyException;
import lab.stoneshelter.exceptions.StoneChatTurnConflictException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.handlers.ApiExceptionHandler;
import lab.stoneshelter.services.StoneChatAdmissionService;
import lab.stoneshelter.services.StoneChatbotService;
import lab.stoneshelter.services.StoneChatOriginService;
import lab.stoneshelter.services.StoneChatSettings;
import lab.stoneshelter.shared.StoneChatHistoryResponse;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import lab.stoneshelter.shared.StoneChatMessageResponse;
import lab.stoneshelter.shared.StoneChatStone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = StoneChatbotControllerTest.ChatConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneChatbotControllerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
    @Import({StoneChatbotController.class, StoneChatHistoryController.class, StoneChatBindingInterceptor.class,
            StoneChatWebConfiguration.class, ApiExceptionHandler.class})
    static class ChatConfiguration {
        @Bean StoneChatAdmissionService admission() {
            StoneChatAdmissionService admission = mock(StoneChatAdmissionService.class);
            StoneChatOriginService origin = new StoneChatOriginService(new StoneChatSettings(new MockEnvironment()
                    .withProperty("CHAT_ALLOWED_ORIGINS", "http://localhost:5173")));
            doAnswer(invocation -> { if (invocation.<Boolean>getArgument(0)) origin.validate(invocation.getArgument(3)); return null; })
                    .when(admission).prepare(anyBoolean(), anyString(), nullable(String.class), nullable(String.class));
            when(admission.bind(nullable(String.class), any())).thenReturn("test-session");
            when(admission.remainingRetentionSeconds(anyString())).thenReturn(86400L);
            when(admission.retentionSeconds()).thenReturn(86400L); return admission;
        }
        @Bean StoneRagConversationService exactSearch() { return mock(StoneRagConversationService.class); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean StoneChatSettings chatSettings() { return new StoneChatSettings(new MockEnvironment()); }
        @Bean StoneChatbotService chatbot() {
            StoneChatbotService service = mock(StoneChatbotService.class);
            when(service.sendMessage(any(), anyString(), any())).thenAnswer(invocation -> {
                StoneChatMessageRequest request = invocation.getArgument(0);
                switch (request.message()) {
                    case "capacity" -> throw new ModelCapacityExceededException();
                    case "budget" -> throw new ModelTokenBudgetExceededException(LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
                    case "invalid-model" -> throw new InvalidStoneExactSearchOutputException();
                    case "unavailable" -> throw new StoneChatUnavailableException();
                    case "busy" -> throw new StoneChatConversationBusyException();
                    case "rate" -> throw new StoneChatRateLimitedException(6);
                    case "conflict" -> throw new StoneChatTurnConflictException();
                    default -> { invocation.<Runnable>getArgument(2).run(); return new StoneChatMessageResponse("Here are some stones you might like.",
                            List.of(new StoneChatStone(1, "Mars"), new StoneChatStone(3, "Luna"))); }
                }
            });
            when(service.findById(any(), anyString())).thenAnswer(invocation -> new StoneChatHistoryResponse(invocation.getArgument(0), List.of()));
            return service;
        }
    }
    @Autowired RestTestClient restClient;
    @Test void exactResponseAndCookieHaveTheAgreedShape() {
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(validRequest("choose"))
                .exchange().expectStatus().isOk().expectHeader().value("Set-Cookie", cookie -> {
                    assertThat(cookie).contains("stone_chat_session=test-session", "Path=/api/v1/chat", "HttpOnly", "SameSite=Lax", "Max-Age=86400");
                    assertThat(cookie).doesNotContain("Domain=");
                }).expectBody().jsonPath("$.text").isEqualTo("Here are some stones you might like.")
                .jsonPath("$.stones[0].name").isEqualTo("Mars").jsonPath("$.stones[1].name").isEqualTo("Luna");
    }
    @Test void originAndRequiredTurnIdAreEnforced() {
        for (String origin : List.of("null", "http://foreign.example")) restClient.post().uri("/api/v1/chat/messages")
                .header("Origin", origin).body(validRequest("choose")).exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_ORIGIN_REJECTED");
        restClient.post().uri("/api/v1/chat/messages").body(validRequest("choose")).exchange().expectStatus().isForbidden();
        var request = new java.util.HashMap<>(validRequest("choose")); request.remove("turnId");
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(request)
                .exchange().expectStatus().isBadRequest().expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
    @Test void translatesGuardErrorsWithCorrectRetryMetadata() {
        for (String message : List.of("busy", "rate")) restClient.post().uri("/api/v1/chat/messages")
                .header("Origin", "http://localhost:5173").body(validRequest(message)).exchange().expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", message.equals("busy") ? "1" : "6");
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(validRequest("unavailable"))
                .exchange().expectStatus().isEqualTo(503).expectHeader().doesNotExist("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_UNAVAILABLE");
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(validRequest("conflict"))
                .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("CHAT_TURN_CONFLICT");
    }
    @Test void modelErrorsUseAgreedStatusAndRetryHintsWithoutSuccessCookie() {
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(validRequest("capacity"))
                .exchange().expectStatus().isEqualTo(503).expectHeader().valueEquals("Retry-After", "1")
                .expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.code").isEqualTo("CHAT_CAPACITY_EXCEEDED");
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(validRequest("budget"))
                .exchange().expectStatus().isEqualTo(429).expectHeader().value("Retry-After", value -> assertThat(Long.parseLong(value)).isBetween(1L, 86400L))
                .expectHeader().doesNotExist("Set-Cookie").expectBody().jsonPath("$.code").isEqualTo("CHAT_TOKEN_BUDGET_EXCEEDED");
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").body(validRequest("invalid-model"))
                .exchange().expectStatus().isEqualTo(502).expectHeader().doesNotExist("Set-Cookie")
                .expectBody().jsonPath("$.code").isEqualTo("CHAT_INVALID_MODEL_OUTPUT");
    }
    @Test void generatesMatchingJsonAndYamlIncludingHistoryAndTurnIdentity() throws Exception {
        JsonNode json = Json31.mapper().readTree(restClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody());
        JsonNode operation = json.at("/paths/~1api~1v1~1chat~1messages/post");
        assertThat(operation.path("description").asText()).contains("Redis", "AVAILABLE", "catalog");
        assertThat(operation.path("responses").propertyStream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("200", "400", "403", "404", "409", "429", "502", "503");
        assertThat(json.at("/components/schemas/StoneChatMessageRequest/required"))
                .contains(Json31.mapper().valueToTree("turnId"));
        assertThat(json.at("/components/schemas/StoneChatMessageRequest/properties/turnId/format").asText()).isEqualTo("uuid");
        assertThat(json.at("/components/schemas/StoneChatMessageResponse/properties").propertyStream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("text", "stones");
        assertThat(json.at("/paths/~1api~1v1~1chat~1conversations~1{conversationId}~1messages/get").isMissingNode()).isFalse();
        assertThat(json.at("/components/schemas/StoneChatHistoryMessage/properties").propertyStream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("role", "text", "stones", "context", "turnId");
        assertThat(json.at("/components/securitySchemes/stoneChatSession/in").asText()).isEqualTo("cookie");
        assertThat(json.at("/components/securitySchemes/stoneChatSession/name").asText()).isEqualTo("stone_chat_session");
        assertThat(operation.at("/responses/429/headers/Retry-After").isMissingNode()).isFalse();
        assertThat(operation.path("parameters").valueStream().anyMatch(parameter -> "Origin".equals(parameter.path("name").asText())
                && parameter.path("required").asBoolean())).isTrue();
        assertThat(json.at("/components/schemas/StoneChatMessageRequest/properties/message/maxLength").asInt()).isEqualTo(2000);
        assertThat(json.at("/components/schemas/StoneChatContext/properties/stoneId/maximum").asLong()).isEqualTo(9007199254740991L);
        assertThat(Yaml31.mapper().readTree(restClient.get().uri("/v3/api-docs.yaml").exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody())).isEqualTo(json);
    }
    static Stream<Object> invalidRequests() {
        List<Object> requests = new ArrayList<>();
        requests.add("{");
        requests.add("null");
        for (String field : List.of("conversationId", "turnId", "message")) {
            var stoneChatMessageRequest = validRequest();
            stoneChatMessageRequest.remove(field);
            requests.add(stoneChatMessageRequest);
            stoneChatMessageRequest = validRequest();
            stoneChatMessageRequest.put(field, null);
            requests.add(stoneChatMessageRequest);
        }
        for (String message : List.of("", " \t\n", "x".repeat(2001))) {
            var stoneChatMessageRequest = validRequest();
            stoneChatMessageRequest.put("message", message);
            requests.add(stoneChatMessageRequest);
        }
        var stoneChatMessageRequest = validRequest();
        stoneChatMessageRequest.put("conversationId", "not-a-uuid");
        requests.add(stoneChatMessageRequest);
        stoneChatMessageRequest = validRequest(); stoneChatMessageRequest.put("turnId", "not-a-uuid"); requests.add(stoneChatMessageRequest);
        for (Object stoneId : List.of(0, -1, 9007199254740992L, "not-an-id")) {
            stoneChatMessageRequest = validRequest();
            stoneChatMessageRequest.put("context", Map.of("stoneId", stoneId));
            requests.add(stoneChatMessageRequest);
        }
        return requests.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidChatRequestsWithProblemDetail(Object stoneChatMessageRequest) {
        restClient.post().uri("/api/v1/chat/messages").header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                .body(stoneChatMessageRequest).exchange().expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(400).jsonPath("$.detail").isNotEmpty();
    }

    private static HashMap<String, Object> validRequest() { return new HashMap<>(validRequest("  Choose a stone  ")); }
    private static Map<String, Object> validRequest(String message) { return Map.of("conversationId", UUID.randomUUID(), "turnId", UUID.randomUUID(), "message", message); }
}
