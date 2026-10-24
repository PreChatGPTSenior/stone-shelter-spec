package lab.stoneshelter;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "chatbot.transport", matches = "true")
@SpringBootTest(classes = StoneChatbotControllerTest.ChatConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StoneChatbotTransportTest {
    @LocalServerPort private int serverPort;

    @Test
    void frontendAdapterAndViteProxiesReachTheActualChatbotEndpoint() throws Exception {
        var log = Files.createTempFile("stone-chatbot-transport-", ".log");
        Process process = null;
        try {
            var processBuilder = new ProcessBuilder("node", "scripts/chatbot-smoke.mjs")
                    .directory(new File("../stone-shelter-ui")).redirectErrorStream(true).redirectOutput(log.toFile());
            processBuilder.environment().put("API_PROXY_TARGET", "http://127.0.0.1:" + serverPort);
            process = processBuilder.start();
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("Chatbot smoke timeout").isTrue();
            assertThat(process.exitValue()).as(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("Chatbot smoke passed");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }
}
