package lab.stoneshelter.services;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoneChatSettings {
    private final Environment environment;
    public StoneChatSettings(Environment environment) { this.environment = environment; }
    public Duration timeout() { return duration("CHAT_REDIS_TIMEOUT", "PT1S"); }
    public Duration ttl() { return duration("CHAT_MEMORY_TTL", "PT24H"); }
    public Duration deadline() { return duration("CHAT_TURN_DEADLINE", "PT120S"); }
    public Duration lease() { return duration("CHAT_TURN_LEASE", "PT150S"); }
    public Duration renewal() { return duration("CHAT_TURN_RENEWAL", "PT30S"); }
    public Duration rateGrace() { return duration("CHAT_RATE_EXPIRY_GRACE", "PT60S"); }
    public long capacity(boolean send, boolean session) {
        return positive("CHAT_" + (send ? "SEND" : "HISTORY") + "_" + (session ? "SESSION" : "IP") + "_BURST",
                send ? (session ? 3 : 10) : (session ? 5 : 20));
    }
    public long refill(boolean send, boolean session) {
        return positive("CHAT_" + (send ? "SEND" : "HISTORY") + "_" + (session ? "SESSION" : "IP") + "_REFILL_PER_MINUTE",
                send ? (session ? 10 : 60) : (session ? 30 : 120));
    }
    public Set<String> origins() { return values("CHAT_ALLOWED_ORIGINS"); }
    public Set<String> proxies() { return values("CHAT_TRUSTED_PROXIES"); }
    private Set<String> values(String name) {
        return Arrays.stream(environment.getProperty(name, "").split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    private Duration duration(String name, String fallback) {
        Duration duration = Duration.parse(environment.getProperty(name, fallback));
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException(name + " must be positive");
        return duration;
    }
    private long positive(String name, long fallback) {
        long value = environment.getProperty(name, Long.class, fallback);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
