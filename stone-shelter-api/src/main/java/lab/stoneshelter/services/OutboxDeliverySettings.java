package lab.stoneshelter.services;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lab.stoneshelter.exceptions.OutboxValidationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Transactional
public class OutboxDeliverySettings {
    public static final String DEAD_LETTER_TOPIC = "outbox-dead-letter";
    private final Environment environment;
    private final Map<String, Route> registeredRoutes = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public record Route(String topic, String keyField) {
    }

    public OutboxDeliverySettings(Environment environment) { this.environment = environment; }

    @Transactional(readOnly = true)
    public Duration pollInterval() { return duration("OUTBOX_POLL_INTERVAL", "PT2S"); }

    @Transactional(readOnly = true)
    public Duration deadLetterRetention() { return duration("OUTBOX_DEAD_LETTER_RETENTION", "PT24H"); }

    @Transactional(readOnly = true)
    public int batchSize() {
        try {
            int batchSize = Integer.parseInt(environment.getProperty("OUTBOX_BATCH_SIZE", "20"));
            if (batchSize > 0) return batchSize;
        } catch (NumberFormatException exception) {
            throw new OutboxValidationException("OUTBOX_BATCH_SIZE must be a positive integer.", exception);
        }
        throw new OutboxValidationException("OUTBOX_BATCH_SIZE must be a positive integer.");
    }

    public Route registerRoute(String eventType, String topic, String keyField) {
        Route route = new Route(topic, keyField);
        registeredRoutes.put(eventType, route);
        return route;
    }

    @Transactional(readOnly = true)
    public Route route(String eventType) {
        try {
            JsonNode routes = jsonMapper.readTree(environment.getProperty("OUTBOX_ROUTES", "{}"));
            if (!routes.isObject()) throw new OutboxValidationException("OUTBOX_ROUTES must be a JSON object.");
            JsonNode route = routes.get(eventType);
            if (route == null) {
                Route registeredRoute = registeredRoutes.get(eventType);
                if (registeredRoute != null) return registeredRoute;
                throw new OutboxValidationException("No Kafka route is configured for event type " + eventType);
            }
            if (!route.isObject() || !route.path("topic").isTextual() || route.path("topic").asText().isBlank()
                    || (route.hasNonNull("keyField") && (!route.path("keyField").isTextual()
                    || route.path("keyField").asText().isBlank()))) {
                throw new OutboxValidationException("The Kafka route requires a topic and optional nonblank keyField.");
            }
            return new Route(route.path("topic").asText(), route.hasNonNull("keyField") ? route.path("keyField").asText() : null);
        } catch (JacksonException exception) {
            throw new OutboxValidationException("OUTBOX_ROUTES is invalid JSON.", exception);
        }
    }

    private Duration duration(String key, String fallback) {
        try {
            Duration duration = Duration.parse(environment.getProperty(key, fallback));
            if (duration.toMillis() > 0) return duration;
        } catch (RuntimeException exception) {
            throw new OutboxValidationException(key + " must be a positive ISO-8601 duration.", exception);
        }
        throw new OutboxValidationException(key + " must be a positive ISO-8601 duration.");
    }
}
