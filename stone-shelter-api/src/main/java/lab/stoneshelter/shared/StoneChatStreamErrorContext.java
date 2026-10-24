package lab.stoneshelter.shared;

import java.util.UUID;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(hidden = true)
public record StoneChatStreamErrorContext(UUID conversationId, UUID turnId, long sequence, ProblemDetail problem) {}
