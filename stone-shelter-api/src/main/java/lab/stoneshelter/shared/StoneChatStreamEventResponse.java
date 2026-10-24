package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(hidden = true)
public record StoneChatStreamEventResponse(String eventName, Object data, RuntimeException failure, long sequence) {}
