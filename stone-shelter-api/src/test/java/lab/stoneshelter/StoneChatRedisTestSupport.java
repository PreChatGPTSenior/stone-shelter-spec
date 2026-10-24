package lab.stoneshelter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import lab.stoneshelter.services.StoneChatSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

abstract class StoneChatRedisTestSupport {
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.2.10-alpine3.22")).withExposedPorts(6379);
    protected final MockEnvironment environment = new MockEnvironment();
    protected final StoneChatSettings settings = new StoneChatSettings(environment);
    protected final List<String> keys = new ArrayList<>();
    protected StoneChatRedisRepository redis;
    private LettuceConnectionFactory connectionFactory;
    @BeforeEach
    void startRedisConnection() {
        synchronized (REDIS) { if (!REDIS.isRunning()) REDIS.start(); }
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());
        connectionFactory.afterPropertiesSet(); connectionFactory.start();
        redis = new StoneChatRedisRepository(connectionFactory, settings);
    }
    protected String key() { String key = "chat:probe:" + UUID.randomUUID(); keys.add(key); return key; }
    protected void prepareRedisConnection() {
        try (var connection = connectionFactory.getConnection()) {
            if (!"PONG".equals(connection.ping())) throw new IllegalStateException("Test Redis connection preparation failed");
        }
    }
    @AfterEach
    void cleanRedisKeys() {
        try { if (redis != null) redis.delete(keys); }
        finally { if (connectionFactory != null) connectionFactory.destroy(); }
    }
}
