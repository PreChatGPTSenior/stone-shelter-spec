package lab.stoneshelter.services;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoneChatStreamSettings {
    private record Limits(int concurrent, int chunk) {}
    private record Timers(Duration delivery, Duration heartbeat, Duration write) {}
    private final Limits limits;
    private final Timers timers;
    public StoneChatStreamSettings(Environment environment) {
        limits = new Limits(positive(environment, "CHAT_STREAM_MAX_CONCURRENT", 4), positive(environment, "CHAT_STREAM_CHUNK_CODE_POINTS", 256));
        timers = new Timers(duration(environment, "CHAT_STREAM_DELIVERY_TIMEOUT", "PT10S"),
                duration(environment, "CHAT_STREAM_HEARTBEAT_INTERVAL", "PT15S"), duration(environment, "CHAT_STREAM_WRITE_TIMEOUT", "PT10S"));
        if (timers.write().compareTo(timers.delivery()) > 0) throw new IllegalArgumentException("CHAT_STREAM_WRITE_TIMEOUT exceeds delivery timeout");
    }
    public int maximumConcurrent() { return limits.concurrent(); }
    public Duration deliveryTimeout() { return timers.delivery(); }
    public Duration heartbeatInterval() { return timers.heartbeat(); }
    public Duration writeTimeout() { return timers.write(); }
    public int chunkCodePoints() { return limits.chunk(); }
    private int positive(Environment environment, String name, int fallback) {
        try { int value = Integer.parseInt(environment.getProperty(name, Integer.toString(fallback))); if (value > 0) return value; }
        catch (NumberFormatException ignored) { /* Report only the setting name. */ }
        throw new IllegalArgumentException(name + " must be a positive integer");
    }
    private Duration duration(Environment environment, String name, String fallback) {
        try {
            Duration value = Duration.parse(environment.getProperty(name, fallback));
            if (!value.isNegative() && value.toMillis() > 0 && value.toNanos() > 0) return value;
        } catch (RuntimeException ignored) { /* Finite representable timer values are required. */ }
        throw new IllegalArgumentException(name + " must be a positive finite millisecond duration");
    }
}
