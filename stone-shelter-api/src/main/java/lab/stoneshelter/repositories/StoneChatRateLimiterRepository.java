package lab.stoneshelter.repositories;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import java.time.Duration;
import java.util.List;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.services.StoneChatSettings;
import org.springframework.stereotype.Repository;

@Repository
public class StoneChatRateLimiterRepository {
    private final StoneChatRedisRepository redis;
    private final StoneChatSettings settings;
    public StoneChatRateLimiterRepository(StoneChatRedisRepository redis, StoneChatSettings settings) {
        this.redis = redis; this.settings = settings;
    }
    public void consume(boolean send, boolean session, String identity) {
        consumeKey("chat:rate:" + (send ? "send" : "history") + ":" + (session ? "session" : "ip") + ":" + identity,
                settings.capacity(send, session), settings.refill(send, session));
    }
    public ConsumptionProbe consumeKey(String key, long capacity, long refill) {
        ConsumptionProbe consumptionProbe = redis.execute(commands -> Bucket4jLettuce.casBasedBuilder(commands)
                .requestTimeout(settings.timeout())
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(settings.rateGrace()))
                .build().asAsync().builder().build(StoneChatRedisRepository.bytes(key), BucketConfiguration.builder()
                        .addLimit(limit -> limit.capacity(capacity).refillGreedy(refill, Duration.ofMinutes(1))).build())
                .tryConsumeAndReturnRemaining(1));
        if (consumptionProbe == null) throw redis.unavailable(new IllegalStateException("Unknown bucket outcome"));
        if (!consumptionProbe.isConsumed()) throw new StoneChatRateLimitedException(
                Math.max(1, (consumptionProbe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L));
        return consumptionProbe;
    }
    public void probe(String prefix) {
        try { consumeKey(prefix + "rate", 1, 60); }
        finally { redis.delete(List.of(prefix + "rate")); }
    }
}
