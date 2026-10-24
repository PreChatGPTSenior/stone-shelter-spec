package lab.stoneshelter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only native I/O qualification. No production route, model or business storage. */
@SpringBootTest(classes = StoneChatStreamTransportQualificationTest.PrototypeConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StoneChatStreamTransportQualificationTest {
    private static final String ABORT = "stone.prototype.abort";
    private static final String REQUEST_TOKEN = "stone.prototype.token";
    private static final String TEXT = "  Basalt 🪨\nLine\r\n<&>  ";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort int port;
    @Autowired PrototypeTransport transport;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
    static class PrototypeConfiguration {
        @Bean PrototypeTransport transport() { return new PrototypeTransport(); }
        @Bean PrototypeController controller(PrototypeTransport transport) { return new PrototypeController(transport); }
        @Bean WebMvcConfigurer prototypeMvc(PrototypeTransport transport) {
            return new WebMvcConfigurer() {
                @Override public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
                    handlers.add(new PrototypeReturnValueHandler(transport));
                }
            };
        }
        @Bean TomcatServletWebServerFactory prototypeServer() {
            var factory = new TomcatServletWebServerFactory(); factory.setPort(0);
            factory.addConnectorCustomizers(connector -> connector.setProperty("socket.txBufSize", "4096"));
            factory.addContextValves(new ValveBase(true) {
                @Override public void invoke(Request request, Response response) throws IOException, ServletException {
                    UUID token = UUID.randomUUID(); request.setAttribute(REQUEST_TOKEN, token);
                    request.setAttribute(ABORT, (Runnable) () -> {
                        if (token.equals(request.getAttribute(REQUEST_TOKEN))) response.getCoyoteResponse().action(ActionCode.CLOSE_NOW, new IOException("Prototype delivery deadline"));
                    });
                    getNext().invoke(request, response);
                }
            });
            return factory;
        }
    }
    @Controller
    static class PrototypeController {
        private final PrototypeTransport transport;
        PrototypeController(PrototypeTransport transport) { this.transport = transport; }
        @PostMapping(path = "/prototype/{id}", produces = "text/event-stream")
        PrototypeHandle open(@PathVariable UUID id) { return transport.open(id); }
    }
    record PrototypeHandle(PrototypeState state) {}
    record PendingFrame(String type, byte[] bytes, AtomicBoolean written, AtomicBoolean flushed) {}
    static class PrototypeState {
        private UUID id;
        private String phase;
        private CompletableFuture<String> result = new CompletableFuture<>();
        private CountDownLatch releaseResult = new CountDownLatch(1);
        private AtomicInteger commits = new AtomicInteger();
        private AtomicBoolean workerExited = new AtomicBoolean();
        private AtomicLong readyAt = new AtomicLong();
        private AtomicBoolean closing = new AtomicBoolean();
        private AtomicBoolean nativeDone = new AtomicBoolean();
        private AtomicBoolean released = new AtomicBoolean();
        private AtomicInteger writers = new AtomicInteger();
        private AtomicInteger backpressure = new AtomicInteger();
        private AtomicInteger maximumPending = new AtomicInteger();
        private AsyncContext async;
        private ServletOutputStream output;
        private Runnable abort;
        private ScheduledFuture<?> tick;
        private volatile PendingFrame pending;
        private int sequence;
        private int textOffset;
        private volatile boolean started;
        private volatile long openedAt;
        private volatile long pendingAt;
        private long heartbeatAt;
        private volatile String saved;
        public PrototypeState() {}
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }
        public CompletableFuture<String> getResult() { return result; }
        public void setResult(CompletableFuture<String> value) { result = value; }
        public CountDownLatch getReleaseResult() { return releaseResult; }
        public void setReleaseResult(CountDownLatch value) { releaseResult = value; }
        public AtomicInteger getCommits() { return commits; }
        public void setCommits(AtomicInteger value) { commits = value; }
        public AtomicBoolean getWorkerExited() { return workerExited; }
        public void setWorkerExited(AtomicBoolean value) { workerExited = value; }
        public AtomicLong getReadyAt() { return readyAt; }
        public void setReadyAt(AtomicLong value) { readyAt = value; }
        public AtomicBoolean getClosing() { return closing; }
        public void setClosing(AtomicBoolean value) { closing = value; }
        public AtomicBoolean getNativeDone() { return nativeDone; }
        public void setNativeDone(AtomicBoolean value) { nativeDone = value; }
        public AtomicBoolean getReleased() { return released; }
        public void setReleased(AtomicBoolean value) { released = value; }
        public AtomicInteger getWriters() { return writers; }
        public void setWriters(AtomicInteger value) { writers = value; }
        public AtomicInteger getBackpressure() { return backpressure; }
        public void setBackpressure(AtomicInteger value) { backpressure = value; }
        public AtomicInteger getMaximumPending() { return maximumPending; }
        public void setMaximumPending(AtomicInteger value) { maximumPending = value; }
        public AsyncContext getAsync() { return async; }
        public void setAsync(AsyncContext value) { async = value; }
        public ServletOutputStream getOutput() { return output; }
        public void setOutput(ServletOutputStream value) { output = value; }
        public Runnable getAbort() { return abort; }
        public void setAbort(Runnable value) { abort = value; }
        public ScheduledFuture<?> getTick() { return tick; }
        public void setTick(ScheduledFuture<?> value) { tick = value; }
        public PendingFrame getPending() { return pending; }
        public void setPending(PendingFrame value) { pending = value; }
        public int getSequence() { return sequence; }
        public void setSequence(int value) { sequence = value; }
        public int getTextOffset() { return textOffset; }
        public void setTextOffset(int value) { textOffset = value; }
        public boolean getStarted() { return started; }
        public void setStarted(boolean value) { started = value; }
        public long getOpenedAt() { return openedAt; }
        public void setOpenedAt(long value) { openedAt = value; }
        public long getPendingAt() { return pendingAt; }
        public void setPendingAt(long value) { pendingAt = value; }
        public long getHeartbeatAt() { return heartbeatAt; }
        public void setHeartbeatAt(long value) { heartbeatAt = value; }
        public String getSaved() { return saved; }
        public void setSaved(String value) { saved = value; }
    }
    static class PrototypeTransport implements AutoCloseable {
        private final Semaphore slots = new Semaphore(4);
        private final Map<UUID, PrototypeState> states = new ConcurrentHashMap<>();
        private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        PrototypeTransport() { timer.setRemoveOnCancelPolicy(true); }
        PrototypeState prepare(String phase) {
            PrototypeState state = new PrototypeState(); state.setId(UUID.randomUUID()); state.setPhase(phase); states.put(state.getId(), state); return state;
        }
        PrototypeHandle open(UUID id) {
            PrototypeState state = states.get(id);
            if (state == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            if (state.getPhase().startsWith("replay")) { state.saved = TEXT; state.readyAt.set(System.nanoTime()); state.result.complete(TEXT); }
            else Thread.ofVirtual().name("prototype-admitted-work").start(() -> {
                try {
                    if (!state.releaseResult.await(5, TimeUnit.SECONDS)) { state.result.completeExceptionally(new IllegalStateException("Controlled processing deadline")); return; }
                    if (state.getPhase().equals("error")) { state.result.completeExceptionally(new IllegalStateException("Controlled processing failure")); return; }
                    state.saved = state.getPhase().equals("delivery-stall") ? "Z🪨".repeat(1024 * 1024) : TEXT; state.commits.incrementAndGet(); state.readyAt.set(System.nanoTime()); state.result.complete(state.saved);
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); state.result.completeExceptionally(exception); }
                finally { state.workerExited.set(true); }
            });
            return new PrototypeHandle(state);
        }
        void attach(PrototypeState state, HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.setContentType("text/event-stream"); response.setHeader("Cache-Control", "no-store"); response.setBufferSize(1024);
            state.openedAt = System.nanoTime(); state.heartbeatAt = state.openedAt;
            state.abort = (Runnable) request.getAttribute(ABORT);
            if (state.abort == null) throw new IllegalStateException("No native connection closer");
            state.async = request.startAsync(); state.async.setTimeout(5000); state.output = response.getOutputStream();
            state.async.addListener(new AsyncListener() {
                @Override public void onComplete(AsyncEvent event) { state.nativeDone.set(true); state.closing.set(true); releaseAfterExit(state); }
                @Override public void onTimeout(AsyncEvent event) { abort(state); }
                @Override public void onError(AsyncEvent event) { abort(state); }
                @Override public void onStartAsync(AsyncEvent event) { throw new IllegalStateException("No redispatch/resume"); }
            });
            state.output.setWriteListener(new WriteListener() {
                @Override public void onWritePossible() { pump(state); }
                @Override public void onError(Throwable failure) { abort(state); }
            });
            state.tick = timer.scheduleAtFixedRate(() -> {
                long now = System.nanoTime();
                if (state.readyAt.get() != 0 && now - state.readyAt.get() >= TimeUnit.MILLISECONDS.toNanos(400)
                        || now - state.openedAt >= TimeUnit.SECONDS.toNanos(5)
                        || state.pending != null && now - state.pendingAt >= TimeUnit.MILLISECONDS.toNanos(400)) abort(state);
                else pump(state);
            }, 20, 20, TimeUnit.MILLISECONDS);
        }
        private void pump(PrototypeState state) {
            if (state.closing.get()) return;
            if (expired(state)) { abort(state); return; }
            state.writers.incrementAndGet();
            try {
                synchronized (state) {
                    while (!state.closing.get()) {
                        if (expired(state)) { abort(state); return; }
                        if (state.pending == null) { state.pending = next(state); state.pendingAt = System.nanoTime(); if (state.pending == null) return; state.maximumPending.accumulateAndGet(1, Math::max); }
                        if (!state.pending.written().get()) {
                            if (!state.output.isReady()) { state.backpressure.incrementAndGet(); return; }
                            state.output.write(state.pending.bytes()); state.pending.written().set(true);
                        }
                        if (!state.pending.flushed().get()) {
                            if (!state.output.isReady()) { state.backpressure.incrementAndGet(); return; }
                            state.output.flush(); state.pending.flushed().set(true);
                        }
                        if (!state.output.isReady()) { state.backpressure.incrementAndGet(); return; }
                        boolean terminal = state.pending.type().equals("completed") || state.pending.type().equals("error");
                        state.pending = null;
                        if (terminal) { state.closing.set(true); state.async.complete(); return; }
                    }
                }
            } catch (IOException | RuntimeException failure) { abort(state); }
            finally { state.writers.decrementAndGet(); releaseAfterExit(state); }
        }
        private PendingFrame next(PrototypeState state) {
            if (!state.started) {
                state.started = true;
                String frame = event(state, "started", Map.of());
                // Bounded valid comment padding forces real TCP backpressure in the preparation flush.
                if (state.getPhase().equals("started-stall")) frame += ": " + "p".repeat(8 * 1024 * 1024) + "\n\n";
                return frame("started", frame);
            }
            if (!state.result.isDone()) {
                long now = System.nanoTime();
                if (now - state.heartbeatAt < TimeUnit.MILLISECONDS.toNanos(100)) return null;
                state.heartbeatAt = now;
                return frame("heartbeat", state.getPhase().equals("heartbeat-stall") ? ": " + "p".repeat(8 * 1024 * 1024) + "\n\n" : ": keep-alive\n\n");
            }
            if (state.result.isCompletedExceptionally()) return frame("error", event(state, "error", Map.of("problem", Map.of("type", "about:blank", "title", "Chat unavailable", "status", 503, "code", "CHAT_UNAVAILABLE"))));
            String text = state.result.getNow(null);
            if (!state.getPhase().startsWith("replay") && state.textOffset < text.length()) {
                int end = state.textOffset;
                for (int index = 0; index < 4 && end < text.length(); index++) end += Character.charCount(text.codePointAt(end));
                String fragment = text.substring(state.textOffset, end); state.textOffset = end; return frame("text-delta", event(state, "text-delta", Map.of("text", fragment)));
            }
            return frame("completed", event(state, "completed", Map.of("text", text, "stones", List.of(Map.of("id", 7, "name", "Fixture stone")))));
        }
        private String event(PrototypeState state, String type, Map<String, Object> fields) {
            var data = JSON.createObjectNode().put("conversationId", state.getId().toString()).put("turnId", state.getId().toString()).put("sequence", ++state.sequence);
            fields.forEach((key, value) -> data.set(key, JSON.valueToTree(value)));
            return "event: " + type + "\ndata: " + data + "\n\n";
        }
        private PendingFrame frame(String type, String data) { return new PendingFrame(type, data.getBytes(StandardCharsets.UTF_8), new AtomicBoolean(), new AtomicBoolean()); }
        private boolean expired(PrototypeState state) {
            long now = System.nanoTime();
            return state.readyAt.get() != 0 && now - state.readyAt.get() >= TimeUnit.MILLISECONDS.toNanos(400)
                    || now - state.openedAt >= TimeUnit.SECONDS.toNanos(5)
                    || state.pending != null && now - state.pendingAt >= TimeUnit.MILLISECONDS.toNanos(400);
        }
        private void abort(PrototypeState state) {
            if (!state.closing.compareAndSet(false, true)) return;
            if (state.abort != null) state.abort.run();
            try { if (state.async != null) state.async.complete(); } catch (IllegalStateException ignored) { /* Native error completion may already be running. */ }
            releaseAfterExit(state);
        }
        private void releaseAfterExit(PrototypeState state) {
            if (state.nativeDone.get() && state.writers.get() == 0 && state.released.compareAndSet(false, true)) {
                if (state.tick != null) state.tick.cancel(false);
                state.pending = null; state.output = null; state.abort = null; slots.release();
            }
        }
        @Override public void close() { states.values().forEach(state -> { state.releaseResult.countDown(); abort(state); }); timer.shutdownNow(); }
    }
    static class PrototypeReturnValueHandler implements HandlerMethodReturnValueHandler {
        private final PrototypeTransport transport;
        PrototypeReturnValueHandler(PrototypeTransport transport) { this.transport = transport; }
        @Override public boolean supportsReturnType(MethodParameter type) { return type.getParameterType() == PrototypeHandle.class; }
        @Override public void handleReturnValue(Object value, MethodParameter type, ModelAndViewContainer container, NativeWebRequest request) throws IOException {
            container.setRequestHandled(true);
            if (((PrototypeHandle) value).state().getPhase().equals("replay-delayed")) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
            transport.attach(((PrototypeHandle) value).state(), request.getNativeRequest(HttpServletRequest.class), request.getNativeResponse(HttpServletResponse.class));
        }
    }

    @Test void qualifyPrototype() throws Exception {
        assertThat(ServerInfo.getServerNumber()).startsWith("11.0.24");
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            PrototypeState ordinary = transport.prepare("normal");
            var ordinaryResponse = client.sendAsync(request(ordinary), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            await(() -> ordinary.started); ordinary.releaseResult.countDown();
            String body = ordinaryResponse.get(3, TimeUnit.SECONDS).body();
            assertThat(body).startsWith("event: started\n").contains("event: text-delta", "event: completed").doesNotContain("event: error");
            StringBuilder fragments = new StringBuilder(); int sequence = 0; int terminal = 0;
            for (String frame : body.split("\n\n")) {
                if (frame.startsWith(":")) continue;
                String[] lines = frame.split("\n"); var data = JSON.readTree(lines[1].substring(6));
                assertThat(data.path("sequence").asInt()).isEqualTo(++sequence); assertThat(data.path("turnId").asString()).isEqualTo(ordinary.getId().toString());
                if (lines[0].equals("event: text-delta")) { fragments.append(data.path("text").asString()); assertThat(data.has("stones")).isFalse(); }
                if (lines[0].equals("event: completed")) { terminal++; assertThat(data.path("text").asString()).isEqualTo(TEXT); }
            }
            assertThat(fragments.toString()).isEqualTo(TEXT); assertThat(terminal).isEqualTo(1); await(() -> ordinary.released.get()); assertThat(ordinary.commits.get()).isEqualTo(1);
            PrototypeState replay = transport.prepare("replay"); String replayBody = client.send(request(replay), HttpResponse.BodyHandlers.ofString()).body();
            assertThat(replayBody).contains("event: started", "event: completed").doesNotContain("text-delta"); await(() -> replay.released.get()); assertThat(replay.commits.get()).isZero();
            PrototypeState error = transport.prepare("error"); var failed = client.sendAsync(request(error), HttpResponse.BodyHandlers.ofString());
            await(() -> error.started); error.releaseResult.countDown(); var failedResponse = failed.get(3, TimeUnit.SECONDS);
            assertThat(failedResponse.statusCode()).isEqualTo(200); assertThat(failedResponse.body()).contains("event: started", "event: error", "CHAT_UNAVAILABLE").doesNotContain("event: completed", "text-delta");
            await(() -> error.released.get()); assertThat(error.commits.get()).isZero();
            PrototypeState expiredReplay = transport.prepare("replay-delayed"); var late = client.sendAsync(request(expiredReplay), HttpResponse.BodyHandlers.ofString());
            await(() -> expiredReplay.released.get()); assertThat(expiredReplay.saved).isEqualTo(TEXT);
            try { assertThat(late.get(3, TimeUnit.SECONDS).body()).doesNotContain("event: completed", "text-delta"); }
            catch (ExecutionException expected) { assertThat(expected.getCause()).isInstanceOf(IOException.class); }
            List<PrototypeState> held = new ArrayList<>(); List<Socket> heldSockets = new ArrayList<>();
            try {
                for (int index = 0; index < 4; index++) { PrototypeState state = transport.prepare("held"); held.add(state); heldSockets.add(socket(state)); }
                await(() -> transport.slots.availablePermits() == 0);
                PrototypeState rejected = transport.prepare("rejected"); var denied = client.send(request(rejected), HttpResponse.BodyHandlers.ofString());
                assertThat(denied.statusCode()).isEqualTo(503); assertThat(denied.body()).doesNotContain("event: started"); assertThat(rejected.commits.get()).isZero();
                assertThat(transport.timer.getQueue().size()).isLessThanOrEqualTo(4);
            } finally { held.forEach(state -> state.releaseResult.countDown()); for (Socket socket : heldSockets) socket.close(); }
            await(() -> transport.slots.availablePermits() == 4);
            for (String phase : List.of("started-stall", "heartbeat-stall", "delivery-stall")) {
                PrototypeState blocked = transport.prepare(phase);
                try (Socket socket = socket(blocked)) {
                    if (phase.equals("delivery-stall")) blocked.releaseResult.countDown();
                    await(() -> blocked.backpressure.get() > 0); blocked.releaseResult.countDown(); await(() -> blocked.commits.get() == 1 && blocked.workerExited.get());
                    await(() -> blocked.released.get()); assertThat(blocked.nativeDone.get()).isTrue(); assertThat(blocked.writers.get()).isZero(); assertThat(blocked.pending).isNull(); assertThat(blocked.maximumPending.get()).isEqualTo(1);
                    assertPeerClosed(socket);
                }
            }
            PrototypeState disconnected = transport.prepare("disconnect"); Socket abandoned = socket(disconnected); abandoned.close();
            await(() -> disconnected.released.get()); assertThat(disconnected.workerExited.get()).isFalse(); assertThat(disconnected.commits.get()).isZero();
            disconnected.releaseResult.countDown(); await(() -> disconnected.commits.get() == 1 && disconnected.workerExited.get()); await(() -> disconnected.released.get());
            assertThat(disconnected.saved).isEqualTo(TEXT); assertThat(disconnected.commits.get()).isEqualTo(1);
            PrototypeState savedDisconnect = transport.prepare("after-save"); Socket lostDelivery = socket(savedDisconnect);
            savedDisconnect.releaseResult.countDown(); await(() -> savedDisconnect.commits.get() == 1); lostDelivery.close();
            await(() -> savedDisconnect.released.get()); assertThat(savedDisconnect.saved).isEqualTo(TEXT); assertThat(savedDisconnect.commits.get()).isEqualTo(1);
            await(() -> transport.slots.availablePermits() == 4 && transport.timer.getQueue().isEmpty());
        }
    }
    private HttpRequest request(PrototypeState state) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/prototype/" + state.getId())).timeout(Duration.ofSeconds(3)).POST(HttpRequest.BodyPublishers.noBody()).build(); }
    private Socket socket(PrototypeState state) throws IOException {
        Socket socket = new Socket(); socket.setReceiveBufferSize(1024); socket.setSoTimeout(2000); socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        socket.getOutputStream().write(("POST /prototype/" + state.getId() + " HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
        int matched = 0; byte[] end = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        while (matched < end.length) { int next = socket.getInputStream().read(); if (next < 0) throw new IOException("No HTTP headers"); matched = next == end[matched] ? matched + 1 : 0; }
        return socket;
    }
    private void assertPeerClosed(Socket socket) throws IOException {
        byte[] bytes = new byte[4096];
        try { while (socket.getInputStream().read(bytes) >= 0) { /* Drain only after deadline to observe actual peer close. */ } }
        catch (SocketException expected) { assertThat(expected.getMessage()).isNotBlank(); }
    }
    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) { if (System.nanoTime() >= deadline) throw new AssertionError("Native resource condition did not complete"); LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2)); }
    }
}
