package lab.stoneshelter.handlers;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.StoneChatConversationBusyException;
import lab.stoneshelter.exceptions.StoneChatConversationNotFoundException;
import lab.stoneshelter.exceptions.StoneChatOriginRejectedException;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.exceptions.StoneChatStreamCapacityExceededException;
import lab.stoneshelter.exceptions.StoneChatTurnConflictException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class StoneChatProblemDetailFactory {
    public record Result(ProblemDetail problem, Long retryAfterSeconds) {}
    public Result createStream(RuntimeException failure) {
        Result result = create(failure);
        if (result.retryAfterSeconds() != null) result.problem().setProperty("retryAfterSeconds", result.retryAfterSeconds());
        return result;
    }
    public Result create(RuntimeException failure) {
        int status = 503; String code = "CHAT_UNAVAILABLE"; Long retry = null;
        String detail = failure instanceof StoneChatUnavailableException ? failure.getMessage() : "Chat is temporarily unavailable. Restore history before retrying.";
        if (failure instanceof ModelCapacityExceededException) { code = "CHAT_CAPACITY_EXCEEDED"; retry = 1L; }
        else if (failure instanceof StoneChatStreamCapacityExceededException) { code = "CHAT_STREAM_CAPACITY_EXCEEDED"; retry = 1L; }
        else if (failure instanceof ModelTokenBudgetExceededException budget) { status = 429; code = "CHAT_TOKEN_BUDGET_EXCEEDED"; retry = Math.max(1, (Duration.between(Instant.now(), budget.getRetryAt()).toMillis() + 999) / 1000); }
        else if (failure instanceof StoneChatRateLimitedException rate) { status = 429; code = "CHAT_RATE_LIMITED"; retry = rate.getRetryAfter(); }
        else if (failure instanceof StoneChatConversationBusyException) { status = 429; code = "CHAT_CONVERSATION_BUSY"; retry = 1L; }
        else if (failure instanceof StoneChatOriginRejectedException) { status = 403; code = "CHAT_ORIGIN_REJECTED"; }
        else if (failure instanceof StoneChatConversationNotFoundException) { status = 404; code = "CHAT_CONVERSATION_NOT_FOUND"; }
        else if (failure instanceof StoneChatTurnConflictException) { status = 409; code = "CHAT_TURN_CONFLICT"; }
        else if (failure instanceof InvalidStoneExactSearchOutputException || failure instanceof InvalidStoneSemanticSearchOutputException || failure instanceof InvalidStoneRagOutputException) { status = 502; code = "CHAT_INVALID_MODEL_OUTPUT"; }
        else if (failure instanceof StoneSemanticSearchLimitExceededException) code = "CHAT_SEMANTIC_LIMIT_EXCEEDED";
        else if (failure instanceof StoneRagAnswerLimitExceededException) code = "CHAT_RAG_LIMIT_EXCEEDED";
        if (!code.equals("CHAT_UNAVAILABLE")) detail = failure.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail); problem.setType(URI.create("about:blank")); problem.setProperty("code", code);
        return new Result(problem, retry);
    }
}
