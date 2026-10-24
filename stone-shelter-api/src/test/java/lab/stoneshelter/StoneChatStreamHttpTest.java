package lab.stoneshelter;

import java.util.List;
import lab.stoneshelter.shared.StoneChatStreamStartedResponse;
import lab.stoneshelter.shared.StoneChatStreamTextDeltaResponse;
import lab.stoneshelter.shared.StoneChatStreamCompletedResponse;
import lab.stoneshelter.shared.StoneChatStreamErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.OpenAPI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StoneChatStreamIntegrationTest.StreamConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class StoneChatStreamHttpTest {
    @Autowired RestTestClient client;
    @Test void generatedJsonAndYamlDescribeTheActualSseLifecycleAndKeepExistingPublicShapes() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml")) {
            String body = client.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
            OpenAPI api = path.endsWith("yaml") ? Yaml31.mapper().readValue(body, OpenAPI.class) : Json31.mapper().readValue(body, OpenAPI.class);
            var operation = api.getPaths().get("/api/v1/chat/messages/stream").getPost();
            assertThat(operation.getRequestBody().getContent().get("application/json").getSchema().get$ref()).endsWith("/StoneChatMessageRequest");
            assertThat(operation.getDescription()).contains("started", "text-delta", "completed", "error", "atomic", "Replay", "disconnect");
            assertThat(operation.getResponses().get("200").getContent().keySet()).containsExactly("text/event-stream");
            assertThat(operation.getResponses().get("503").getDescription()).contains("CHAT_STREAM_CAPACITY_EXCEEDED");
            assertThat(operation.getResponses().get("503").getHeaders()).containsKey("Retry-After");
            assertThat(api.getComponents().getSchemas().get(StoneChatStreamStartedResponse.class.getSimpleName()).getProperties().keySet()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence");
            assertThat(api.getComponents().getSchemas().get(StoneChatStreamTextDeltaResponse.class.getSimpleName()).getProperties().keySet()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence", "text");
            assertThat(api.getComponents().getSchemas().get(StoneChatStreamCompletedResponse.class.getSimpleName()).getProperties().keySet()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence", "text", "stones");
            assertThat(api.getComponents().getSchemas().get(StoneChatStreamErrorResponse.class.getSimpleName()).getProperties().keySet()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence", "problem");
            assertThat(api.getComponents().getSchemas()).doesNotContainKey("StoneChatStreamResponse");
            assertThat(api.getComponents().getSchemas().get("StoneChatMessageResponse").getProperties().keySet()).containsExactlyInAnyOrder("text", "stones");
            assertThat(api.getComponents().getSchemas().get("StoneChatHistoryMessage").getProperties().keySet()).containsExactlyInAnyOrder("role", "text", "stones", "context", "turnId");
        }
    }
}
