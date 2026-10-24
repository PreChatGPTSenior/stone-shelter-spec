package lab.stoneshelter.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lab.stoneshelter.mappers.dtos.StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper;
import lab.stoneshelter.services.StoneChatStreamSettings;
import lab.stoneshelter.shared.StoneChatStreamResponse;
import lab.stoneshelter.shared.StoneChatStreamEventResponse;
import lab.stoneshelter.shared.StoneChatStreamErrorContext;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

/** Bounded nonblocking Servlet delivery; completion never cancels admitted business work. */
@Component
public class StoneChatStreamReturnValueHandler implements HandlerMethodReturnValueHandler {
    public static final String ABORT = "stone.chat.stream.abort";
    public static final String REQUEST_TOKEN = "stone.chat.stream.native-token";
    private record PendingFrame(String name, byte[] bytes, AtomicBoolean written, AtomicBoolean flushed) {}
    private record Configuration(StoneChatStreamSettings settings, Clock clock) {}
    private record Mapping(JsonMapper jsonMapper, StoneChatProblemDetailFactory problems, StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper errorMapper) {}
    private record Resources(ScheduledThreadPoolExecutor timer, ThreadPoolExecutor writers, Set<Transport> transports) {}
    private final Configuration configuration;
    private final Mapping mapping;
    private final Resources resources;
    public StoneChatStreamReturnValueHandler(StoneChatStreamSettings settings, JsonMapper jsonMapper, StoneChatProblemDetailFactory problems,
            StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper errorMapper, Clock clock) {
        configuration = new Configuration(settings, clock); mapping = new Mapping(jsonMapper, problems, errorMapper);
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "chat-stream-delivery"); thread.setDaemon(true); return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        resources = new Resources(timer, new ThreadPoolExecutor(settings.maximumConcurrent(), settings.maximumConcurrent(), 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "chat-stream-writer"); thread.setDaemon(true); return thread;
        }), ConcurrentHashMap.newKeySet());
    }
    @Override public boolean supportsReturnType(MethodParameter type) { return type.getParameterType() == StoneChatStreamResponse.class; }
    @Override public void handleReturnValue(Object value, MethodParameter type, ModelAndViewContainer container, NativeWebRequest webRequest) throws IOException {
        container.setRequestHandled(true);
        StoneChatStreamResponse stream = (StoneChatStreamResponse) value;
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
        Transport transport = new Transport(); transport.stream = stream;
        try {
            transport.abort = (Runnable) request.getAttribute(ABORT);
            if (transport.abort == null) throw new IllegalStateException("Native stream connection closer is unavailable");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.setContentType("text/event-stream"); response.setHeader("Cache-Control", "no-store");
            transport.heartbeatAt = System.nanoTime(); transport.async = request.startAsync();
            transport.async.setTimeout(Math.max(1, Duration.between(configuration.clock().instant(), stream.getProcessingDeadline()).plus(configuration.settings().deliveryTimeout()).toMillis()));
            transport.output = response.getOutputStream(); resources.transports().add(transport);
            transport.async.addListener(new AsyncListener() {
                @Override public void onComplete(AsyncEvent event) { transport.closing.set(true); transport.nativeDone.set(true); releaseAfterExit(transport); }
                @Override public void onTimeout(AsyncEvent event) { abort(transport); }
                @Override public void onError(AsyncEvent event) { abort(transport); }
                @Override public void onStartAsync(AsyncEvent event) { abort(transport); }
            });
            transport.output.setWriteListener(new WriteListener() {
                @Override public void onWritePossible() { requestPump(transport); }
                @Override public void onError(Throwable failure) { abort(transport); }
            });
            transport.tick = resources.timer().scheduleAtFixedRate(() -> { if (expired(transport)) abort(transport); else requestPump(transport); }, 20, 20, TimeUnit.MILLISECONDS);
            if (transport.released.get()) transport.tick.cancel(false);
        } catch (IOException | RuntimeException failure) {
            if (transport.async == null) stream.getOnTransportExit().run(); else abort(transport);
            throw failure;
        }
    }
    private void requestPump(Transport transport) {
        synchronized (transport) {
            if (transport.closing.get() || !transport.pumping.compareAndSet(false, true)) return;
            transport.writers.incrementAndGet();
        }
        try { resources.writers().execute(() -> pump(transport)); }
        catch (RejectedExecutionException rejected) {
            synchronized (transport) { transport.pumping.set(false); transport.writers.decrementAndGet(); releaseAfterExit(transport); }
        }
    }
    private void pump(Transport transport) {
        try {
            if (!transport.writeLock.tryLock()) return;
            try {
                while (!transport.closing.get()) {
                    if (expired(transport)) { abort(transport); return; }
                    if (transport.pending == null) {
                        transport.pendingAt = System.nanoTime(); transport.pending = next(transport);
                        if (transport.pending == null) return;
                    }
                    if (transport.closing.get() || expired(transport)) { abort(transport); return; }
                    if (!transport.pending.written().get()) {
                        if (!transport.output.isReady()) return;
                        transport.output.write(transport.pending.bytes()); transport.pending.written().set(true);
                    }
                    if (!transport.pending.flushed().get()) {
                        if (!transport.output.isReady()) return;
                        transport.output.flush(); transport.pending.flushed().set(true);
                    }
                    if (!transport.output.isReady()) return;
                    boolean terminal = transport.pending.name().equals("completed") || transport.pending.name().equals("error");
                    transport.pending = null;
                    if (terminal) { transport.closing.set(true); transport.async.complete(); return; }
                }
            } finally { transport.writeLock.unlock(); }
        } catch (IOException | RuntimeException failure) { abort(transport); }
        finally { synchronized (transport) { transport.pumping.set(false); transport.writers.decrementAndGet(); releaseAfterExit(transport); } }
    }
    private PendingFrame next(Transport transport) {
        StoneChatStreamEventResponse event = transport.stream.getNextEvent().get();
        if (event == null) {
            if (transport.stream.getConfirmedAtNanos().getAsLong() != Long.MIN_VALUE || System.nanoTime() - transport.heartbeatAt < configuration.settings().heartbeatInterval().toNanos()) return null;
            transport.heartbeatAt = System.nanoTime(); return frame("heartbeat", ": keep-alive\n\n");
        }
        Object data = event.failure() == null ? event.data() : mapping.errorMapper().toDto(new StoneChatStreamErrorContext(transport.stream.getConversationId(), transport.stream.getTurnId(), event.sequence(), mapping.problems().createStream(event.failure()).problem()));
        return frame(event.eventName(), "event: " + event.eventName() + "\ndata: " + mapping.jsonMapper().writeValueAsString(data) + "\n\n");
    }
    private PendingFrame frame(String name, String data) { return new PendingFrame(name, data.getBytes(StandardCharsets.UTF_8), new AtomicBoolean(), new AtomicBoolean()); }
    private boolean expired(Transport transport) {
        if (transport.closing.get()) return false;
        long now = System.nanoTime(); long confirmedAt = transport.stream.getConfirmedAtNanos().getAsLong();
        return (confirmedAt == Long.MIN_VALUE ? !configuration.clock().instant().isBefore(transport.stream.getProcessingDeadline()) : now - confirmedAt >= configuration.settings().deliveryTimeout().toNanos())
                || transport.pending != null && now - transport.pendingAt >= configuration.settings().writeTimeout().toNanos();
    }
    private void abort(Transport transport) {
        if (!transport.closing.compareAndSet(false, true)) return;
        try { if (transport.abort != null) transport.abort.run(); }
        finally {
            try { if (transport.async != null) transport.async.complete(); } catch (IllegalStateException ignored) { /* Native completion may already be running. */ }
            releaseAfterExit(transport);
        }
    }
    private void releaseAfterExit(Transport transport) {
        synchronized (transport) {
            if (transport.nativeDone.get() && transport.writers.get() == 0 && transport.released.compareAndSet(false, true)) {
                if (transport.tick != null) transport.tick.cancel(false);
                transport.pending = null; transport.output = null; transport.abort = null; resources.transports().remove(transport);
                transport.stream.getOnTransportExit().run();
            }
        }
    }
    public int activeTransports() { return resources.transports().size(); }
    public int scheduledTransports() { return resources.timer().getQueue().size(); }
    @PreDestroy public void close() { resources.transports().forEach(this::abort); resources.timer().shutdownNow(); resources.writers().shutdownNow(); }

    private static class Transport {
        private StoneChatStreamResponse stream;
        private AsyncContext async;
        private ServletOutputStream output;
        private Runnable abort;
        private AtomicBoolean pumping = new AtomicBoolean();
        private AtomicBoolean closing = new AtomicBoolean();
        private AtomicBoolean nativeDone = new AtomicBoolean();
        private AtomicBoolean released = new AtomicBoolean();
        private AtomicInteger writers = new AtomicInteger();
        private ReentrantLock writeLock = new ReentrantLock();
        private volatile PendingFrame pending;
        private volatile long pendingAt;
        private long heartbeatAt;
        private volatile ScheduledFuture<?> tick;
        public Transport() {}
        public AtomicBoolean getPumping() { return pumping; }
        public void setPumping(AtomicBoolean value) { pumping = value; }
        public StoneChatStreamResponse getStream() { return stream; }
        public void setStream(StoneChatStreamResponse value) { stream = value; }
        public AsyncContext getAsync() { return async; }
        public void setAsync(AsyncContext value) { async = value; }
        public ServletOutputStream getOutput() { return output; }
        public void setOutput(ServletOutputStream value) { output = value; }
        public Runnable getAbort() { return abort; }
        public void setAbort(Runnable value) { abort = value; }
        public AtomicBoolean getClosing() { return closing; }
        public void setClosing(AtomicBoolean value) { closing = value; }
        public AtomicBoolean getNativeDone() { return nativeDone; }
        public void setNativeDone(AtomicBoolean value) { nativeDone = value; }
        public AtomicBoolean getReleased() { return released; }
        public void setReleased(AtomicBoolean value) { released = value; }
        public AtomicInteger getWriters() { return writers; }
        public void setWriters(AtomicInteger value) { writers = value; }
        public ReentrantLock getWriteLock() { return writeLock; }
        public void setWriteLock(ReentrantLock value) { writeLock = value; }
        public PendingFrame getPending() { return pending; }
        public void setPending(PendingFrame value) { pending = value; }
        public long getPendingAt() { return pendingAt; }
        public void setPendingAt(long value) { pendingAt = value; }
        public long getHeartbeatAt() { return heartbeatAt; }
        public void setHeartbeatAt(long value) { heartbeatAt = value; }
        public ScheduledFuture<?> getTick() { return tick; }
        public void setTick(ScheduledFuture<?> value) { tick = value; }
    }
}
