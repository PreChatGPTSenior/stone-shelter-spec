package lab.stoneshelter;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(classes = StoneChatbotControllerTest.ChatConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneChatHistoryControllerTest {
    @Autowired RestTestClient restClient;
    @Test void normalAbsenceReturnsRequestedIdentityAndSynchronizesRemainingCookieLifetime() {
        UUID id = UUID.randomUUID();
        restClient.get().uri("/api/v1/chat/conversations/" + id + "/messages").exchange().expectStatus().isOk()
                .expectHeader().valueMatches("Set-Cookie", ".*Max-Age=86400.*").expectBody().jsonPath("$.conversationId").isEqualTo(id.toString())
                .jsonPath("$.messages").isEmpty();
    }
    @Test void malformedPathUuidIsBadRequest() {
        restClient.get().uri("/api/v1/chat/conversations/not-a-uuid/messages").exchange().expectStatus().isBadRequest();
    }
}
