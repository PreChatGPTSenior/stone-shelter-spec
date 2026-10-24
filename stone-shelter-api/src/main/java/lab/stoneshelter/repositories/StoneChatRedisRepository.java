package lab.stoneshelter.repositories;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.services.StoneChatSettings;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Repository;

@Repository
public class StoneChatRedisRepository {
    private final RedisConnectionFactory redisConnectionFactory;
    private final StoneChatSettings settings;
    private volatile Consumer<Throwable> failureListener = failure -> {};
    public StoneChatRedisRepository(RedisConnectionFactory redisConnectionFactory, StoneChatSettings settings) {
        this.redisConnectionFactory = redisConnectionFactory; this.settings = settings;
    }
    public void setFailureListener(Consumer<Throwable> listener) { failureListener = listener; }
    public StoneChatUnavailableException unavailable(Throwable cause) {
        failureListener.accept(cause);
        return new StoneChatUnavailableException(cause);
    }
    public <T> T execute(Function<RedisAsyncCommands<byte[], byte[]>, CompletionStage<T>> operation) {
        long deadline = System.nanoTime() + settings.timeout().toNanos();
        try (RedisConnection redisConnection = redisConnectionFactory.getConnection()) {
            @SuppressWarnings("unchecked")
            RedisAsyncCommands<byte[], byte[]> commands = (RedisAsyncCommands<byte[], byte[]>) redisConnection.getNativeConnection();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new java.util.concurrent.TimeoutException();
            CompletionStage<T> result = operation.apply(commands);
            remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new java.util.concurrent.TimeoutException();
            T value = result.toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
            return value;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw unavailable(exception);
        }
    }
    public String get(String key) {
        byte[] value = execute(commands -> commands.get(bytes(key)));
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }
    public long eval(String script, List<String> keys, String... arguments) {
        Long value = execute(commands -> commands.eval(script, ScriptOutputType.INTEGER,
                keys.stream().map(StoneChatRedisRepository::bytes).toArray(byte[][]::new),
                java.util.Arrays.stream(arguments).map(StoneChatRedisRepository::bytes).toArray(byte[][]::new)));
        if (value == null) throw unavailable(new IllegalStateException("Unknown Redis script result"));
        return value;
    }
    public void delete(List<String> keys) {
        if (!keys.isEmpty()) execute(commands -> commands.del(keys.stream().map(StoneChatRedisRepository::bytes).toArray(byte[][]::new)));
    }
    public void probe(String prefix) {
        String key = prefix + "storage";
        try {
            execute(commands -> commands.psetex(bytes(key), Duration.ofSeconds(10).toMillis(), bytes(prefix)));
            if (!prefix.equals(get(key))) throw unavailable(new IllegalStateException("Redis probe mismatch"));
        } finally { delete(List.of(key)); }
    }
    public static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
