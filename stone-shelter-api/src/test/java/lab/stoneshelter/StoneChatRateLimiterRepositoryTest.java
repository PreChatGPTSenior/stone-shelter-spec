package lab.stoneshelter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatRateLimiterRepositoryTest extends StoneChatRedisTestSupport {
    @Test
    void concurrentClientsCannotExceedCapacityAndExpiryCoversFullRefillPlusGrace() throws Exception {
        String key = key();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < 20; i++) requests.add(() -> {
                try { new StoneChatRateLimiterRepository(redis, settings).consumeKey(key, 3, 1); return true; }
                catch (StoneChatRateLimitedException exception) { return false; }
            });
            int consumed = 0;
            for (var result : executor.invokeAll(requests)) if (result.get()) consumed++;
            assertThat(consumed).isEqualTo(3);
        }
        long ttl = redis.execute(commands -> commands.pttl(StoneChatRedisRepository.bytes(key)));
        assertThat(ttl).isBetween(235_000L, 241_000L);
        assertThatThrownBy(() -> new StoneChatRateLimiterRepository(redis, settings).consumeKey(key, 3, 1))
                .isInstanceOf(StoneChatRateLimitedException.class);
    }
    @Test
    void distinctSendAndHistoryBucketsDoNotResetEachOther() {
        StoneChatRateLimiterRepository limiter = new StoneChatRateLimiterRepository(redis, settings);
        String send = key(); String history = key();
        limiter.consumeKey(send, 1, 1);
        assertThatThrownBy(() -> limiter.consumeKey(send, 1, 1)).isInstanceOf(StoneChatRateLimitedException.class);
        assertThat(limiter.consumeKey(history, 1, 1).isConsumed()).isTrue();
    }
    @Test
    void defaultsDistinguishSessionAndIpBurstFromRefill() {
        assertThat(settings.capacity(true, true)).isEqualTo(3);
        assertThat(settings.refill(true, true)).isEqualTo(10);
        assertThat(settings.capacity(true, false)).isEqualTo(10);
        assertThat(settings.refill(true, false)).isEqualTo(60);
        assertThat(settings.capacity(false, true)).isEqualTo(5);
        assertThat(settings.refill(false, true)).isEqualTo(30);
        assertThat(settings.capacity(false, false)).isEqualTo(20);
        assertThat(settings.refill(false, false)).isEqualTo(120);
    }
}
