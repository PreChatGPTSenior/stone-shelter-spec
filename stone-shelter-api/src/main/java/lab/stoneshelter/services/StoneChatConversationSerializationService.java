package lab.stoneshelter.services;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lab.stoneshelter.exceptions.StoneChatConversationBusyException;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneChatConversationSerializationService {
    private final StoneChatActiveTurnRepository repository;
    private final StoneChatSettings settings;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-lease-renewal"); thread.setDaemon(true); return thread;
    });
    public StoneChatConversationSerializationService(StoneChatActiveTurnRepository repository, StoneChatSettings settings) {
        this.repository = repository; this.settings = settings;
    }
    private record Entry(Bulkhead bulkhead, AtomicInteger references) {}
    private record Lifecycle(long deadline, AtomicBoolean valid, ScheduledFuture<?> renewal, AtomicBoolean closed) {}
    public Guard acquire(String sessionId, UUID conversationId) {
        String key = StoneChatActiveTurnRepository.key(sessionId, conversationId);
        Entry entry = entries.compute(key, (ignored, existing) -> {
            Entry result = existing == null ? new Entry(Bulkhead.of(key, BulkheadConfig.custom()
                    .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build()), new AtomicInteger()) : existing;
            result.references().incrementAndGet(); return result;
        });
        if (!entry.bulkhead().tryAcquirePermission()) { retire(key, entry); throw new StoneChatConversationBusyException(); }
        String token = UUID.randomUUID().toString();
        try {
            if (!repository.acquire(sessionId, conversationId, token)) throw new StoneChatConversationBusyException();
            long deadline = System.nanoTime() + settings.deadline().toNanos();
            AtomicBoolean valid = new AtomicBoolean(true);
            ScheduledFuture<?> renewal = scheduler.scheduleAtFixedRate(() -> {
                synchronized (valid) {
                if (!valid.get()) return;
                try {
                    if (System.nanoTime() >= deadline || !repository.renew(key, token)) {
                        valid.set(false); repository.unavailable(new IllegalStateException("Turn deadline or lease ownership lost"));
                    }
                } catch (RuntimeException exception) { valid.set(false); repository.unavailable(exception); }
                }
            }, settings.renewal().toMillis(), settings.renewal().toMillis(), TimeUnit.MILLISECONDS);
            return new Guard(key, token, entry, new Lifecycle(deadline, valid, renewal, new AtomicBoolean()));
        } catch (RuntimeException exception) {
            entry.bulkhead().onComplete(); retire(key, entry); throw exception;
        }
    }
    private void retire(String key, Entry entry) {
        entries.compute(key, (ignored, current) -> {
            if (current != entry) throw repository.unavailable(new IllegalStateException("Lost bulkhead tracking"));
            return entry.references().decrementAndGet() == 0 ? null : entry;
        });
    }
    public void probe() {
        if (settings.lease().compareTo(settings.deadline()) <= 0 || settings.renewal().compareTo(settings.lease()) >= 0)
            throw repository.unavailable(new IllegalStateException("Invalid turn timing configuration"));
        Bulkhead bulkhead = Bulkhead.of("chat-probe", BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        if (!bulkhead.tryAcquirePermission()) throw repository.unavailable(new IllegalStateException("Uninitialized serialization"));
        try { if (bulkhead.tryAcquirePermission()) throw repository.unavailable(new IllegalStateException("Broken serialization")); }
        finally { bulkhead.onComplete(); }
        if (entries.values().stream().anyMatch(entry -> entry.references().get() <= 0))
            throw repository.unavailable(new IllegalStateException("Broken live turn tracking"));
    }
    @PreDestroy public void close() { scheduler.shutdownNow(); }
    public final class Guard implements AutoCloseable {
        private final String key;
        private final String token;
        private final Entry entry;
        private final Lifecycle lifecycle;
        private Guard(String key, String token, Entry entry, Lifecycle lifecycle) {
            this.key = key; this.token = token; this.entry = entry; this.lifecycle = lifecycle;
        }
        public String token() { check(); return token; }
        public void check() {
            if (!lifecycle.valid().get() || System.nanoTime() >= lifecycle.deadline())
                throw repository.unavailable(new IllegalStateException("Turn is no longer allowed to progress"));
        }
        @Override public void close() {
            if (!lifecycle.closed().compareAndSet(false, true)) return;
            synchronized (lifecycle.valid()) { lifecycle.valid().set(false); lifecycle.renewal().cancel(false); }
            try { repository.release(key, token); }
            finally { entry.bulkhead().onComplete(); retire(key, entry); }
        }
    }
}
