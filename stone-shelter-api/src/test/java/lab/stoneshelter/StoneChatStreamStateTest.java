package lab.stoneshelter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lab.stoneshelter.entities.StoneChatAdmittedTurnEntity;
import lab.stoneshelter.entities.StoneChatStreamEntity;
import lab.stoneshelter.entities.StoneChatTurnEntity;
import lab.stoneshelter.entities.StoneChatStoneEntity;
import lab.stoneshelter.exceptions.MapperValidationException;
import lab.stoneshelter.exceptions.StoneChatStreamCapacityExceededException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.mappers.dtos.StoneChatStreamEntityToStoneChatStreamResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper;
import lab.stoneshelter.mappers.dtos.StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper;
import lab.stoneshelter.services.StoneChatStreamSettings;
import lab.stoneshelter.services.StoneChatStreamAdmissionService;
import lab.stoneshelter.shared.StoneChatStreamErrorContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneChatStreamStateTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    @Test void initialSettingsAndUnrepresentableOrConflictingBoundsAreExplicit() {
        var defaults = new StoneChatStreamSettings(new MockEnvironment());
        assertThat(defaults.maximumConcurrent()).isEqualTo(4); assertThat(defaults.chunkCodePoints()).isEqualTo(256);
        assertThat(defaults.deliveryTimeout()).isEqualTo(Duration.ofSeconds(10)); assertThat(defaults.writeTimeout()).isEqualTo(Duration.ofSeconds(10)); assertThat(defaults.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        for (String name : List.of("CHAT_STREAM_MAX_CONCURRENT", "CHAT_STREAM_CHUNK_CODE_POINTS")) for (String value : List.of("0", "-1", "2147483648", "x"))
            assertThatThrownBy(() -> new StoneChatStreamSettings(new MockEnvironment().withProperty(name, value))).isInstanceOf(IllegalArgumentException.class);
        for (String name : List.of("CHAT_STREAM_DELIVERY_TIMEOUT", "CHAT_STREAM_WRITE_TIMEOUT", "CHAT_STREAM_HEARTBEAT_INTERVAL")) for (String value : List.of("PT0S", "PT-1S", "PT0.000000001S", "P999999999999999999999D", "x"))
            assertThatThrownBy(() -> new StoneChatStreamSettings(new MockEnvironment().withProperty(name, value))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoneChatStreamSettings(new MockEnvironment().withProperty("CHAT_STREAM_WRITE_TIMEOUT", "PT11S"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void capacityHasNoWaitingQueueAndOnlyItsOwnersReleaseOnce() throws Exception {
        var admission = new StoneChatStreamAdmissionService(new StoneChatStreamSettings(new MockEnvironment()));
        List<StoneChatStreamAdmissionService.Lease> leases = new ArrayList<>(); for (int index = 0; index < 4; index++) leases.add(admission.acquire());
        var denied = CompletableFuture.runAsync(() -> assertThatThrownBy(admission::acquire).isInstanceOf(StoneChatStreamCapacityExceededException.class)); denied.get(1, TimeUnit.SECONDS);
        leases.getFirst().close(); leases.getFirst().close(); var replacement = admission.acquire();
        assertThatThrownBy(admission::acquire).isInstanceOf(StoneChatStreamCapacityExceededException.class);
        replacement.close(); leases.forEach(StoneChatStreamAdmissionService.Lease::close);
        List<StoneChatStreamAdmissionService.Lease> restored = new ArrayList<>(); for (int index = 0; index < 4; index++) restored.add(admission.acquire()); restored.forEach(StoneChatStreamAdmissionService.Lease::close);
    }
    @Test void onlyConfirmedImmutableResultsProduceLiteralUnicodeDeltasAndOneTerminal() {
        StoneChatStreamEntity stream = stream(false); var mapper = mapper(1); var handle = mapper.toDto(stream);
        var started = handle.getNextEvent().get(); assertThat(started.eventName()).isEqualTo("started"); assertThat(started.sequence()).isEqualTo(1);
        assertThat(jsonMapper.valueToTree(started.data()).propertyNames()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence");
        assertThat(handle.getNextEvent().get()).isNull(); assertThat(handle.getConfirmedAtNanos().getAsLong()).isEqualTo(Long.MIN_VALUE);
        StoneChatTurnEntity saved = saved(stream, " 🪨\r\n<&> "); assertThat(stream.confirm(saved)).isTrue(); saved.setText("changed after save"); saved.setStones(List.of());
        StringBuilder text = new StringBuilder(); long sequence = 1;
        while (true) {
            var event = handle.getNextEvent().get(); assertThat(event.sequence()).isEqualTo(++sequence);
            var data = jsonMapper.valueToTree(event.data());
            if (event.eventName().equals("completed")) {
                assertThat(data.propertyNames()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence", "text", "stones");
                assertThat(data.path("text").asString()).isEqualTo(" 🪨\r\n<&> "); assertThat(data.path("stones").get(0).path("id").asLong()).isEqualTo(7); break;
            }
            assertThat(data.propertyNames()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence", "text");
            assertThat(data.path("text").asString().codePointCount(0, data.path("text").asString().length())).isEqualTo(1); text.append(data.path("text").asString());
        }
        assertThat(text.toString()).isEqualTo(" 🪨\r\n<&> "); assertThat(handle.getNextEvent().get()).isNull();
        assertThat(stream.confirm(saved(stream, "second answer"))).isFalse(); assertThat(handle.getConfirmedAtNanos().getAsLong()).isEqualTo(123);
        assertThat(handle.getRenewCookie()).isTrue(); assertThat(handle.getReplay()).isFalse();
    }
    @Test void replayErrorAndEmptyReplyHaveExactTerminalSemantics() {
        StoneChatStreamEntity replay = stream(true); assertThat(replay.confirm(saved(replay, "Retained."))).isTrue(); var replayHandle = mapper(256).toDto(replay);
        assertThat(replayHandle.getRenewCookie()).isFalse(); assertThat(replayHandle.getNextEvent().get().eventName()).isEqualTo("started"); assertThat(replayHandle.getNextEvent().get().eventName()).isEqualTo("completed"); assertThat(replayHandle.getNextEvent().get()).isNull();
        StoneChatStreamEntity empty = stream(false); empty.confirm(saved(empty, "")); var emptyHandle = mapper(256).toDto(empty);
        assertThat(emptyHandle.getNextEvent().get().eventName()).isEqualTo("started"); assertThat(emptyHandle.getNextEvent().get().eventName()).isEqualTo("completed");
        StoneChatStreamEntity failed = stream(false); StoneChatUnavailableException cause = new StoneChatUnavailableException(); assertThat(failed.fail(cause)).isTrue(); var failureHandle = mapper(256).toDto(failed);
        assertThat(failureHandle.getNextEvent().get().eventName()).isEqualTo("started"); var error = failureHandle.getNextEvent().get(); assertThat(error.eventName()).isEqualTo("error"); assertThat(error.failure()).isSameAs(cause); assertThat(error.data()).isNull(); assertThat(failureHandle.getNextEvent().get()).isNull();
        ProblemDetail problem = ProblemDetail.forStatus(503); problem.setProperty("code", "CHAT_UNAVAILABLE");
        var publicError = new StoneChatStreamErrorContextToStoneChatStreamErrorResponseMapper().toDto(new StoneChatStreamErrorContext(failureHandle.getConversationId(), failureHandle.getTurnId(), error.sequence(), problem));
        assertThat(jsonMapper.valueToTree(publicError).propertyNames()).containsExactlyInAnyOrder("conversationId", "turnId", "sequence", "problem");
    }
    @Test void transportExitDoesNotCancelAdmittedCompletionAndMappingRejectsNull() {
        StoneChatStreamEntity stream = stream(false); AtomicInteger releases = new AtomicInteger(); stream.setReleaseTransport(releases::incrementAndGet); var handle = mapper(256).toDto(stream);
        handle.getOnTransportExit().run(); handle.getOnTransportExit().run(); assertThat(releases.get()).isEqualTo(1); assertThat(stream.getCompletion().isCancelled()).isFalse();
        assertThat(stream.confirm(saved(stream, "Saved after disconnect."))).isTrue(); assertThat(stream.getCompletion().getNow(null).text()).isEqualTo("Saved after disconnect."); assertThat(handle.getNextEvent().get()).isNull();
        assertThatThrownBy(() -> mapper(256).toDto(null)).isInstanceOf(MapperValidationException.class);
        StoneChatStreamEntity identity = stream(false); StoneChatTurnEntity wrong = saved(identity, "wrong"); wrong.setTurnId(UUID.randomUUID());
        assertThatThrownBy(() -> identity.confirm(wrong)).isInstanceOf(IllegalArgumentException.class); assertThat(identity.getCompletion().isDone()).isFalse();
    }
    private StoneChatStreamEntity stream(boolean replay) {
        StoneChatTurnEntity request = new StoneChatTurnEntity(); request.setTurnId(UUID.randomUUID()); request.setMessage("Literal request.");
        StoneChatAdmittedTurnEntity admitted = new StoneChatAdmittedTurnEntity(); admitted.setConversationId(UUID.randomUUID()); admitted.setRequestTurn(request); admitted.setDeadline(Instant.now().plusSeconds(120)); admitted.setSessionId("private-test-session");
        if (replay) admitted.setReplayTurn(request);
        StoneChatStreamEntity stream = new StoneChatStreamEntity(); stream.setAdmittedTurn(admitted); stream.setNanoClock(() -> 123L); stream.setReleaseTransport(() -> {}); return stream;
    }
    private StoneChatTurnEntity saved(StoneChatStreamEntity stream, String text) { StoneChatTurnEntity saved = new StoneChatTurnEntity(); saved.setTurnId(stream.getAdmittedTurn().getRequestTurn().getTurnId()); saved.setText(text); saved.setStones(List.of(new StoneChatStoneEntity(7, "Fixture stone"))); return saved; }
    private StoneChatStreamEntityToStoneChatStreamResponseMapper mapper(int chunk) { return new StoneChatStreamEntityToStoneChatStreamResponseMapper(new StoneChatStreamEventEntityToStoneChatStreamEventResponseMapper(), new StoneChatStreamSettings(new MockEnvironment().withProperty("CHAT_STREAM_CHUNK_CODE_POINTS", Integer.toString(chunk)))); }
}
