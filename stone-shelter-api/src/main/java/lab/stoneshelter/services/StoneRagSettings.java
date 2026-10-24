package lab.stoneshelter.services;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoneRagSettings {
    private final int outputTokens;
    private final int maximumGenerationCalls;
    public StoneRagSettings(Environment environment, StoneExactSearchSettings modelSettings) {
        try {
            outputTokens = Integer.parseInt(environment.getProperty("CHAT_RAG_OUTPUT_TOKENS", "1024"));
            maximumGenerationCalls = Integer.parseInt(environment.getProperty("CHAT_RAG_MAX_GENERATION_CALLS", "4"));
            if (outputTokens < 1 || outputTokens >= modelSettings.contextTokens() || maximumGenerationCalls < 1 || maximumGenerationCalls > 4)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("Unqualified RAG output/call settings", exception); }
    }
    public int outputTokens() { return outputTokens; }
    public int maximumGenerationCalls() { return maximumGenerationCalls; }
    public Duration finalizationReserve() { return Duration.ofSeconds(5); }
}
