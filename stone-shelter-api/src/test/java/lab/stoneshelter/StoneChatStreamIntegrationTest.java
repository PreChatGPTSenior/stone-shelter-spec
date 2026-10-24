package lab.stoneshelter;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lab.stoneshelter.controllers.StoneChatStreamController;
import lab.stoneshelter.controllers.StoneChatStreamWebConfiguration;
import lab.stoneshelter.entities.StoneChatStreamEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.entities.StoneRagPreparedTurnEntity;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneChatStreamCapacityExceededException;
import lab.stoneshelter.services.StoneChatStreamAdmissionService;
import lab.stoneshelter.entities.StoneChatStreamEventEntity;
import lab.stoneshelter.enums.StoneChatStreamEventType;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.handlers.StoneChatProblemDetailFactory;
import lab.stoneshelter.handlers.StoneChatStreamReturnValueHandler;
import lab.stoneshelter.mappers.dtos.StoneChatConversationEntityToStoneChatHistoryResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatTurnEntityToStoneChatMessageResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatStreamEntityToStoneChatStreamResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper;
import lab.stoneshelter.mappers.entities.StoneChatMessageRequestToStoneChatTurnEntityMapper;
import lab.stoneshelter.repositories.StoneChatActiveTurnRepository;
import lab.stoneshelter.repositories.StoneChatConversationRepository;
import lab.stoneshelter.repositories.StoneChatRateLimiterRepository;
import lab.stoneshelter.repositories.StoneChatRedisRepository;
import lab.stoneshelter.repositories.StoneChatSessionRepository;
import lab.stoneshelter.services.ModelInvocationAdmissionService;
import lab.stoneshelter.services.StoneChatAdmissionService;
import lab.stoneshelter.services.StoneChatAvailabilityService;
import lab.stoneshelter.services.StoneChatClientAddressService;
import lab.stoneshelter.services.StoneChatContextSelector;
import lab.stoneshelter.services.StoneChatConversationSerializationService;
import lab.stoneshelter.services.StoneChatOriginService;
import lab.stoneshelter.services.StoneChatSettings;
import lab.stoneshelter.services.StoneChatStreamSettings;
import lab.stoneshelter.services.StoneChatStreamService;
import lab.stoneshelter.services.StoneChatbotService;
import lab.stoneshelter.services.StoneExactSearchProvider;
import lab.stoneshelter.services.StoneRagConversationService;
import lab.stoneshelter.shared.StoneChatMessageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.net.SocketException;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Actual HTTP/Redis lifetime qualification with controlled already-checked business outcomes. */
@SpringBootTest(classes = StoneChatStreamIntegrationTest.StreamConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"CHAT_STREAM_DELIVERY_TIMEOUT=PT0.6S", "CHAT_STREAM_WRITE_TIMEOUT=PT0.3S", "CHAT_STREAM_HEARTBEAT_INTERVAL=PT0.1S", "CHAT_STREAM_CHUNK_CODE_POINTS=256"})
class StoneChatStreamIntegrationTest extends StoneChatRedisTestSupport {
    private static final String TEXT = "  Basalt 🪨\nLine\r\n<&>  ";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Configuration(proxyBeanMethods = false)
    @Import({StoneChatbotControllerTest.ChatConfiguration.class, StoneChatStreamController.class, StoneChatStreamWebConfiguration.class,
            StoneChatStreamService.class, StoneChatStreamSettings.class, StoneChatStreamAdmissionService.class,
            StoneChatStreamReturnValueHandler.class, StoneChatProblemDetailFactory.class,
            StoneChatStreamEntityToStoneChatStreamResponseMapper.class, StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper.class,
            StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper.class})
    static class StreamConfiguration {
        @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> boundedTestSocket() {
            return factory -> factory.addConnectorCustomizers(connector -> connector.setProperty("socket.txBufSize", "4096"));
        }
    }
    @LocalServerPort int port;
    @Autowired StoneChatbotService httpChatbot;
    @Autowired StoneChatAdmissionService httpAdmission;
    @Autowired StoneChatStreamReturnValueHandler transport;
    @Autowired StoneChatStreamAdmissionService streamAdmission;
    private StoneChatbotService chatbot;
    private StoneChatConversationRepository history;
    private StoneChatConversationSerializationService serialization;
    private StoneChatAvailabilityService availability;
    private StoneChatSessionRepository sessions;
    private String session;
    private StoneChatAdmissionService admission;
    private StoneRagConversationService rag;
    private List<StoneChatStreamEntity> streams = Collections.synchronizedList(new ArrayList<>());
    private AtomicInteger work = new AtomicInteger();
    private CountDownLatch release = new CountDownLatch(0);
    private AtomicBoolean entered = new AtomicBoolean();
    private HttpClient client;

    @BeforeEach void prepare() throws Exception {
        prepareRedisConnection(); reset(httpAdmission, httpChatbot);
        keys.addAll(List.of("chat:rate:send:ip:127.0.0.1", "chat:rate:history:ip:127.0.0.1", "chat:rate:send:ip:0:0:0:0:0:0:0:1", "chat:rate:history:ip:0:0:0:0:0:0:0:1"));
        sessions = new StoneChatSessionRepository(redis, settings); session = sessions.findOrCreate(null).id(); keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), "chat:rate:send:session:" + session, "chat:rate:history:session:" + session));
        history = new StoneChatConversationRepository(redis, settings);
        var active = new StoneChatActiveTurnRepository(redis, settings); serialization = new StoneChatConversationSerializationService(active, settings);
        availability = new StoneChatAvailabilityService(sessions, history, new StoneChatRateLimiterRepository(redis, settings), active, redis, serialization, new StoneChatContextSelector());
        availability.configureModels(mock(StoneExactSearchProvider.class), mock(ModelInvocationAdmissionService.class)); availability.recover();
        var configured = new StoneChatSettings(new MockEnvironment().withProperty("CHAT_ALLOWED_ORIGINS", "http://localhost:5173"));
        admission = new StoneChatAdmissionService(availability, new StoneChatOriginService(configured), new StoneChatClientAddressService(configured), new StoneChatRateLimiterRepository(redis, settings), sessions, settings);
        doAnswer(call -> { admission.prepare(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)); return null; }).when(httpAdmission).prepare(anyBoolean(), anyString(), nullable(String.class), nullable(String.class));
        when(httpAdmission.bind(nullable(String.class), any())).thenAnswer(call -> {
            String id = admission.bind(call.getArgument(0), call.getArgument(1)); synchronized (keys) { keys.add(StoneChatSessionRepository.sessionKey(id)); } return id;
        });
        when(httpAdmission.retentionSeconds()).thenReturn(settings.ttl().toSeconds());
        when(httpAdmission.remainingRetentionSeconds(anyString())).thenAnswer(call -> admission.remainingRetentionSeconds(call.getArgument(0)));
        rag = mock(StoneRagConversationService.class);
        when(rag.complete(any(), any(), any())).thenAnswer(call -> {
            work.incrementAndGet(); entered.set(true); if (!release.await(5, TimeUnit.SECONDS)) throw new StoneChatUnavailableException();
            StoneChatTurnEntity turn = call.getArgument(0);
            if (turn.getMessage().equals("error")) throw new InvalidStoneRagOutputException();
            turn.setText(turn.getMessage().equals("large") ? "Z🪨".repeat(32768) : TEXT); turn.setStones(List.of(new StoneChatStoneEntity(7, "Fixture stone")));
            return new StoneRagPreparedTurnEntity(turn, null);
        });
        installChatbot();
        when(httpChatbot.startMessage(any(), anyString(), any())).thenAnswer(call -> {
            StoneChatStreamEntity stream = chatbot.startMessage(call.getArgument(0), call.getArgument(1), call.<Supplier<Runnable>>getArgument(2)); streams.add(stream); return stream;
        });
        when(httpChatbot.sendMessage(any(), anyString(), any())).thenAnswer(call -> chatbot.sendMessage(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(httpChatbot.findById(any(), anyString())).thenAnswer(call -> chatbot.findById(call.getArgument(0), call.getArgument(1)));
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    @AfterEach void close() throws Exception {
        release.countDown(); await(() -> streams.stream().allMatch(stream -> stream.getCompletion().isDone())); await(() -> transport.activeTransports() == 0);
        if (client != null) client.close(); if (serialization != null) serialization.close();
    }
    private void installChatbot() {
        chatbot = new StoneChatbotService(history, serialization, admission, availability, new StoneChatMessageRequestToStoneChatTurnEntityMapper(),
                new StoneChatTurnEntityToStoneChatMessageResponseMapper(), new StoneChatConversationEntityToStoneChatHistoryResponseMapper(), new StoneChatContextSelector());
        chatbot.configureRagSearch(rag, Clock.systemUTC(), settings);
    }
    private StoneChatMessageRequest request(String message) {
        UUID conversation = UUID.randomUUID(); keys.addAll(List.of(StoneChatSessionRepository.ownerKey(conversation), StoneChatActiveTurnRepository.key(session, conversation), StoneChatConversationRepository.key(session, conversation)));
        return new StoneChatMessageRequest(conversation, UUID.randomUUID(), message, null);
    }
    private HttpRequest post(StoneChatMessageRequest request, String suffix) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/chat/messages" + suffix)).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json").header("Origin", "http://localhost:5173").header("Cookie", "stone_chat_session=" + session)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(request))).build();
    }
    private HttpResponse<String> send(StoneChatMessageRequest request) throws Exception { return client.send(post(request, "/stream"), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
    private List<JsonNode> events(String body) {
        List<JsonNode> events = new ArrayList<>(); long sequence = 0;
        for (String frame : body.split("\n\n")) if (frame.startsWith("event: ")) {
            String[] lines = frame.split("\n"); JsonNode data = JSON.readTree(lines[1].substring(6)); assertThat(data.path("sequence").asLong()).isEqualTo(++sequence); events.add(data);
        }
        return events;
    }
    @Test void confirmedLiteralDeliveryReplayJsonAndHistoryUseOneSavedPairAndCookieLifetimes() throws Exception {
        var request = request("normal"); var response = send(request);
        assertThat(response.statusCode()).isEqualTo(200); assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream"); assertThat(response.headers().firstValue("Set-Cookie")).isPresent();
        var data = events(response.body()); StringBuilder fragments = new StringBuilder();
        for (JsonNode event : data) { assertThat(event.path("turnId").asString()).isEqualTo(request.turnId().toString()); if (event.has("text") && !event.has("stones")) fragments.append(event.path("text").asString()); }
        assertThat(fragments.toString()).isEqualTo(TEXT); assertThat(data.getLast().path("text").asString()).isEqualTo(TEXT);
        assertThat(data.getLast().path("stones").get(0).path("id").asLong()).isEqualTo(7); assertThat(history.findById(session, request.conversationId()).turns()).hasSize(1);
        long ttl = sessions.remainingRetentionMillis(session);
        var replay = send(request); assertThat(replay.body()).doesNotContain("text-delta"); assertThat(events(replay.body())).hasSize(2); assertThat(replay.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(sessions.remainingRetentionMillis(session)).isLessThanOrEqualTo(ttl); assertThat(work.get()).isEqualTo(1);
        var jsonReplay = client.send(post(request, ""), HttpResponse.BodyHandlers.ofString()); assertThat(jsonReplay.statusCode()).isEqualTo(200); assertThat(JSON.readTree(jsonReplay.body()).path("text").asString()).isEqualTo(TEXT);
        assertThat(jsonReplay.headers().firstValue("Set-Cookie")).isEmpty(); assertThat(work.get()).isEqualTo(1);
        var get = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/chat/conversations/" + request.conversationId() + "/messages")).header("Cookie", "stone_chat_session=" + session).GET().build();
        var restored = client.send(get, HttpResponse.BodyHandlers.ofString()); assertThat(restored.statusCode()).isEqualTo(200); assertThat(JSON.readTree(restored.body()).path("messages")).hasSize(2);
        assertThat(restored.headers().firstValue("Set-Cookie").orElseThrow()).contains("Max-Age=" + ttl / 1000); assertThat(sessions.remainingRetentionMillis(session)).isLessThanOrEqualTo(ttl);
    }
    @Test void changedRetainedPayloadIsRejectedBeforeOpening() throws Exception {
        var request = request("normal"); assertThat(send(request).statusCode()).isEqualTo(200);
        var changed = new StoneChatMessageRequest(request.conversationId(), request.turnId(), "changed", null);
        var conflict = send(changed); assertThat(conflict.statusCode()).isEqualTo(409); assertThat(conflict.body()).contains("CHAT_TURN_CONFLICT").doesNotContain("event: started"); assertThat(conflict.headers().firstValue("Set-Cookie")).isEmpty();
    }
    @Test void disconnectBeforeSaveStopsOnlyTransportAndPreservesOneLateConfirmedPair() throws Exception {
        release = new CountDownLatch(1); var request = request("normal");
        var response = client.send(post(request, "/stream"), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200); await(entered::get); assertThat(history.findById(session, request.conversationId()).turns()).isEmpty();
        response.body().close(); await(() -> transport.activeTransports() == 0);
        release.countDown(); await(() -> history.findById(session, request.conversationId()).turns().size() == 1);
        assertThat(send(request).body()).doesNotContain("text-delta"); assertThat(work.get()).isEqualTo(1);
    }
    @Test void fourHeldStreamsRejectFifthWithoutWorkAndDoNotCloseHealthyJsonHistory() throws Exception {
        release = new CountDownLatch(1); List<InputStream> bodies = new ArrayList<>();
        try {
            for (int index = 0; index < 4; index++) {
                session = sessions.findOrCreate(null).id(); keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), "chat:rate:send:session:" + session));
                var opened = client.send(post(request("held"), "/stream"), HttpResponse.BodyHandlers.ofInputStream()); assertThat(opened.statusCode()).isEqualTo(200); bodies.add(opened.body());
            }
            await(() -> transport.activeTransports() == 4 && work.get() == 4); assertThat(transport.scheduledTransports()).isLessThanOrEqualTo(4);
            session = sessions.findOrCreate(null).id(); keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), "chat:rate:send:session:" + session));
            var fifth = request("fifth"); var denied = send(fifth); assertThat(denied.statusCode()).isEqualTo(503); assertThat(denied.body()).contains("CHAT_STREAM_CAPACITY_EXCEEDED").doesNotContain("event: started");
            assertThat(denied.headers().firstValue("Retry-After")).hasValue("1"); assertThat(denied.headers().firstValue("Set-Cookie")).isEmpty(); assertThat(work.get()).isEqualTo(4); availability.assertReady();
            assertThat(redis.get(StoneChatSessionRepository.ownerKey(fifth.conversationId()))).isNull();
            assertThat(chatbot.findById(fifth.conversationId(), session).messages()).isEmpty();
        } finally { release.countDown(); for (InputStream body : bodies) body.close(); }
        await(() -> transport.activeTransports() == 0 && transport.scheduledTransports() == 0);
    }
    @Test void postOpenFailureHasMappedTerminalProblemAndNoPartialHistoryOrSuccess() throws Exception {
        var request = request("error"); var failed = send(request);
        assertThat(failed.statusCode()).isEqualTo(200); assertThat(failed.body()).contains("event: started", "event: error", "CHAT_INVALID_MODEL_OUTPUT").doesNotContain("text-delta", "event: completed");
        var error = events(failed.body()).getLast(); assertThat(error.path("problem").path("status").asInt()).isEqualTo(502); assertThat(error.path("problem").has("properties")).isFalse();
        assertThat(history.findById(session, request.conversationId()).turns()).isEmpty(); availability.assertReady();
    }
    @Test void unknownCommitAcknowledgementDeliversNoTextAndRecoversSavedPairWithoutRegeneration() throws Exception {
        var request = request("normal"); var lose = new AtomicBoolean(true); history = spy(history);
        doAnswer(call -> { Object saved = call.callRealMethod(); if (lose.compareAndSet(true, false)) throw new StoneChatUnavailableException(); return saved; }).when(history).append(anyString(), any(), anyString(), any());
        installChatbot();
        var failed = send(request); assertThat(failed.statusCode()).isEqualTo(200);
        assertThat(failed.body()).contains("event: error", "CHAT_UNAVAILABLE").doesNotContain("text-delta", "event: completed");
        assertThat(history.findById(session, request.conversationId()).turns()).hasSize(1);
        availability.recover(); var replay = send(request); assertThat(replay.body()).contains("event: completed").doesNotContain("text-delta");
        assertThat(work.get()).isEqualTo(1); assertThat(history.findById(session, request.conversationId()).turns()).hasSize(1);
    }
    @Test void preOpenOriginAndInputRejectionsEmitNoStartedOrEarlyCookie() throws Exception {
        var request = request("normal");
        var denied = HttpRequest.newBuilder(post(request, "/stream").uri()).header("Content-Type", "application/json").header("Origin", "http://foreign.invalid").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(request))).build();
        var origin = client.send(denied, HttpResponse.BodyHandlers.ofString()); assertThat(origin.statusCode()).isEqualTo(403); assertThat(origin.body()).doesNotContain("event: started"); assertThat(origin.headers().firstValue("Set-Cookie")).isEmpty();
        var invalid = send(new StoneChatMessageRequest(request.conversationId(), request.turnId(), " ", null)); assertThat(invalid.statusCode()).isEqualTo(400); assertThat(invalid.headers().firstValue("Set-Cookie")).isEmpty(); assertThat(work.get()).isZero();
    }
    @Test void foreignAndExpiredCookiesCannotTransferRetainedOwnershipOrRenewDeniedStreams() throws Exception {
        var request = request("normal"); assertThat(send(request).statusCode()).isEqualTo(200); String originalSession = session;
        session = sessions.findOrCreate(null).id(); keys.addAll(List.of(StoneChatSessionRepository.sessionKey(session), "chat:rate:send:session:" + session));
        var foreign = send(request); assertThat(foreign.statusCode()).isEqualTo(404); assertThat(foreign.body()).contains("CHAT_CONVERSATION_NOT_FOUND").doesNotContain("event: started");
        assertThat(foreign.headers().firstValue("Set-Cookie")).isEmpty();
        session = originalSession; redis.eval("return redis.call('PEXPIRE', KEYS[1], 20)", List.of(StoneChatSessionRepository.sessionKey(session)));
        await(() -> redis.get(StoneChatSessionRepository.sessionKey(originalSession)) == null);
        var expired = send(request); assertThat(expired.statusCode()).isEqualTo(404); assertThat(expired.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(expired.body()).doesNotContain("event: started"); assertThat(work.get()).isEqualTo(1);
        assertThat(history.findById(originalSession, request.conversationId()).turns()).hasSize(1);
        assertThat(originalSession.equals(redis.get(StoneChatSessionRepository.ownerKey(request.conversationId())))).isTrue();
    }
    @Test void qualifyFinalTransport() throws Exception {
        release = new CountDownLatch(1); var request = request("large");
        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(1024); socket.setSoTimeout(3000); socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
            byte[] body = JSON.writeValueAsBytes(request);
            socket.getOutputStream().write(("POST /api/v1/chat/messages/stream HTTP/1.1\r\nHost: 127.0.0.1\r\nOrigin: http://localhost:5173\r\nCookie: stone_chat_session=" + session
                    + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(body); socket.getOutputStream().flush();
            byte[] delimiter = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII); int matched = 0;
            while (matched < delimiter.length) { int next = socket.getInputStream().read(); if (next < 0) throw new IOException("Missing HTTP headers"); matched = next == delimiter[matched] ? matched + 1 : 0; }
            release.countDown(); await(() -> history.findById(session, request.conversationId()).turns().size() == 1);
            await(() -> transport.activeTransports() == 0 && transport.scheduledTransports() == 0);
            StringBuilder delivered = new StringBuilder(); byte[] bytes = new byte[4096];
            try { for (int count; (count = socket.getInputStream().read(bytes)) >= 0;) delivered.append(new String(bytes, 0, count, StandardCharsets.UTF_8)); }
            catch (SocketException expected) { assertThat(expected.getMessage()).isNotBlank(); }
            assertThat(delivered.toString()).doesNotContain("event: completed");
            assertThat(history.findById(session, request.conversationId()).turns().getFirst().getText()).isEqualTo("Z🪨".repeat(32768));
            assertThat(work.get()).isEqualTo(1);
        }
    }
    @Test void replayDeliveryDeadlineStartsAtSavedReadinessIncludingPreWriterDelay() throws Exception {
        var request = request("normal"); assertThat(send(request).statusCode()).isEqualTo(200);
        doAnswer(call -> {
            StoneChatStreamEntity stream = chatbot.startMessage(call.getArgument(0), call.getArgument(1), call.<Supplier<Runnable>>getArgument(2)); streams.add(stream);
            var saved = stream.getCompletion().getNow(null);
            stream.setCompletion(CompletableFuture.completedFuture(new StoneChatStreamEntity.SavedResult(saved.text(), saved.stones(), System.nanoTime() - TimeUnit.SECONDS.toNanos(1))));
            return stream;
        }).when(httpChatbot).startMessage(any(), anyString(), any());
        try { assertThat(send(request).body()).doesNotContain("text-delta", "event: completed"); }
        catch (IOException expected) { assertThat(expected.getMessage()).isNotBlank(); }
        await(() -> transport.activeTransports() == 0); assertThat(work.get()).isEqualTo(1); assertThat(history.findById(session, request.conversationId()).turns()).hasSize(1);
    }
    @Test void blockedFramePreparationCannotBlockWatchdogOrReleaseALiveWriterSlot() throws Exception {
        var request = request("normal"); CountDownLatch enteredFrame = new CountDownLatch(1); CountDownLatch releaseFrame = new CountDownLatch(1);
        doAnswer(call -> {
            StoneChatStreamEntity source = chatbot.startMessage(call.getArgument(0), call.getArgument(1), call.<Supplier<Runnable>>getArgument(2)); streams.add(source);
            StoneChatStreamEntity decorated = new StoneChatStreamEntity() {
                @Override public StoneChatStreamEventEntity nextEvent(int size) {
                    StoneChatStreamEventEntity event = source.nextEvent(size);
                    if (event != null && event.getType() == StoneChatStreamEventType.TEXT_DELTA) {
                        enteredFrame.countDown();
                        try { if (!releaseFrame.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test frame preparation did not exit"); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                    }
                    return event;
                }
            };
            decorated.setAdmittedTurn(source.getAdmittedTurn()); decorated.setCompletion(source.getCompletion()); decorated.setReleaseTransport(source::transportExited); return decorated;
        }).when(httpChatbot).startMessage(any(), anyString(), any());
        var delivery = client.sendAsync(post(request, "/stream"), HttpResponse.BodyHandlers.ofString());
        List<StoneChatStreamAdmissionService.Lease> otherSlots = new ArrayList<>();
        try {
            assertThat(enteredFrame.await(2, TimeUnit.SECONDS)).isTrue();
            try { assertThat(delivery.get(2, TimeUnit.SECONDS).body()).doesNotContain("event: completed", "text-delta"); }
            catch (ExecutionException expected) { assertThat(expected.getCause()).isInstanceOf(IOException.class); }
            assertThat(transport.activeTransports()).isEqualTo(1);
            for (int index = 0; index < 3; index++) otherSlots.add(streamAdmission.acquire());
            assertThatThrownBy(streamAdmission::acquire).isInstanceOf(StoneChatStreamCapacityExceededException.class);
            assertThat(history.findById(session, request.conversationId()).turns()).hasSize(1);
        } finally { releaseFrame.countDown(); otherSlots.forEach(StoneChatStreamAdmissionService.Lease::close); }
        await(() -> transport.activeTransports() == 0 && transport.scheduledTransports() == 0); assertThat(work.get()).isEqualTo(1);
    }
    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) { if (System.nanoTime() >= deadline) throw new AssertionError("Actual stream resource/work did not finish"); LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2)); }
    }
}
