package lab.stoneshelter.handlers;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import lab.stoneshelter.exceptions.ModelCapacityExceededException;
import lab.stoneshelter.exceptions.ModelTokenBudgetExceededException;
import lab.stoneshelter.exceptions.ModelAdmissionDeferredException;
import lab.stoneshelter.exceptions.InvalidStoneExactSearchOutputException;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.exceptions.InvalidStoneSemanticSearchOutputException;
import lab.stoneshelter.exceptions.StoneSemanticSearchLimitExceededException;
import lab.stoneshelter.exceptions.StoneCardReindexConflictException;
import lab.stoneshelter.exceptions.InvalidStoneCardReindexRequestException;
import lab.stoneshelter.exceptions.StoneCardReindexNotFoundException;
import lab.stoneshelter.exceptions.OutboxWriteException;
import lab.stoneshelter.exceptions.InvalidStoneDocumentIndexRequestException;
import lab.stoneshelter.exceptions.StoneDocumentIndexConflictException;
import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import lab.stoneshelter.exceptions.StoneDocumentSourceNotFoundException;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import jakarta.servlet.http.HttpServletResponse;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneChatStreamCapacityExceededException;
import lab.stoneshelter.exceptions.StoneChatRateLimitedException;
import lab.stoneshelter.exceptions.StoneChatConversationBusyException;
import lab.stoneshelter.exceptions.StoneChatConversationNotFoundException;
import lab.stoneshelter.exceptions.StoneChatOriginRejectedException;
import lab.stoneshelter.exceptions.StoneChatTurnConflictException;

import lab.stoneshelter.exceptions.InvalidAdmissionDateRangeException;
import lab.stoneshelter.exceptions.InvalidPhotoException;
import lab.stoneshelter.exceptions.InvalidStonePhotoDraftException;
import lab.stoneshelter.exceptions.StonePhotoDraftConflictException;
import lab.stoneshelter.exceptions.StonePhotoDraftNotFoundException;
import lab.stoneshelter.exceptions.UnsupportedPhotoTypeException;
import lab.stoneshelter.exceptions.PhotoTooLargeException;
import lab.stoneshelter.exceptions.PhotoStorageUnavailableException;
import lab.stoneshelter.exceptions.StoneNotFoundException;
import lab.stoneshelter.exceptions.StoneReservationConflictException;
import lab.stoneshelter.exceptions.MapperValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final StoneChatProblemDetailFactory chatProblems = new StoneChatProblemDetailFactory();
    @ExceptionHandler(StoneCardReindexConflictException.class)
    public ProblemDetail reindexConflict(StoneCardReindexConflictException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidStoneCardReindexRequestException.class)
    public ProblemDetail reindexInvalid(InvalidStoneCardReindexRequestException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StoneCardReindexNotFoundException.class)
    public ProblemDetail reindexMissing(StoneCardReindexNotFoundException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(OutboxWriteException.class)
    public ProblemDetail outboxFailure(OutboxWriteException exception) {
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The catalog change could not be committed.");
    }

    @ExceptionHandler(InvalidStoneDocumentIndexRequestException.class)
    public ProblemDetail documentInvalid(InvalidStoneDocumentIndexRequestException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StoneDocumentIndexConflictException.class)
    public ProblemDetail documentConflict(StoneDocumentIndexConflictException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(StoneDocumentSourceNotFoundException.class)
    public ProblemDetail documentMissing(StoneDocumentSourceNotFoundException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(StoneDocumentSourceUnindexableException.class)
    public ProblemDetail documentUnindexable(StoneDocumentSourceUnindexableException exception) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(StoneDocumentDependencyUnavailableException.class)
    public ProblemDetail documentUnavailable(StoneDocumentDependencyUnavailableException exception) {
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(StoneReservationConflictException.class)
    public ProblemDetail handleReservationConflict(StoneReservationConflictException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidStonePhotoDraftException.class)
    public ProblemDetail handleInvalidDraftReferences(InvalidStonePhotoDraftException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(StonePhotoDraftConflictException.class)
    public ProblemDetail handleDraftConflict(StonePhotoDraftConflictException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(StonePhotoDraftNotFoundException.class)
    public ProblemDetail handleDraftNotFound(StonePhotoDraftNotFoundException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(StoneNotFoundException.class)
    public ProblemDetail handleNotFound(StoneNotFoundException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
    @ExceptionHandler(MapperValidationException.class)
    public ProblemDetail handleMapperValidation(MapperValidationException exception) {
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(InvalidAdmissionDateRangeException.class)
    public ProblemDetail handleInvalidAdmissionDateRange(InvalidAdmissionDateRangeException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidPhotoException.class)
    public ProblemDetail handleInvalidPhoto(InvalidPhotoException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(UnsupportedPhotoTypeException.class)
    public ProblemDetail handleUnsupportedPhoto(UnsupportedPhotoTypeException exception) {
        return problemDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
    }

    @ExceptionHandler(PhotoTooLargeException.class)
    public ProblemDetail handlePhotoTooLarge(PhotoTooLargeException exception) {
        return problemDetail(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage());
    }

    @ExceptionHandler(PhotoStorageUnavailableException.class)
    public ProblemDetail handlePhotoStorageUnavailable(PhotoStorageUnavailableException exception) {
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handlePersistenceFailure(DataAccessException exception) {
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The operation could not be saved. Try again later.");
    }

    @ExceptionHandler(ModelCapacityExceededException.class)
    public ProblemDetail modelCapacity(ModelCapacityExceededException exception, HttpServletResponse response) {
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(ModelTokenBudgetExceededException.class)
    public ProblemDetail modelBudget(ModelTokenBudgetExceededException exception, HttpServletResponse response) {
        long milliseconds = Duration.between(Instant.now(), exception.getRetryAt()).toMillis();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (milliseconds + 999) / 1000)));
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(InvalidStoneExactSearchOutputException.class)
    public ProblemDetail invalidModelOutput(InvalidStoneExactSearchOutputException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(InvalidStoneSemanticSearchOutputException.class)
    public ProblemDetail invalidSemanticOutput(InvalidStoneSemanticSearchOutputException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneSemanticSearchLimitExceededException.class)
    public ProblemDetail semanticLimit(StoneSemanticSearchLimitExceededException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(InvalidStoneRagOutputException.class)
    public ProblemDetail invalidRagOutput(InvalidStoneRagOutputException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneRagAnswerLimitExceededException.class)
    public ProblemDetail ragLimit(StoneRagAnswerLimitExceededException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(ModelAdmissionDeferredException.class)
    public ProblemDetail indexAdmissionDeferred(ModelAdmissionDeferredException exception) {
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Indexing admission is temporarily unavailable.");
    }

    @ExceptionHandler(StoneChatStreamCapacityExceededException.class)
    public ProblemDetail chatStreamCapacity(StoneChatStreamCapacityExceededException exception, HttpServletResponse response) {
        response.setHeader(HttpHeaders.RETRY_AFTER, "1"); return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneChatUnavailableException.class)
    public ProblemDetail chatUnavailable(StoneChatUnavailableException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneChatOriginRejectedException.class)
    public ProblemDetail chatOrigin(StoneChatOriginRejectedException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneChatConversationNotFoundException.class)
    public ProblemDetail chatNotFound(StoneChatConversationNotFoundException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneChatTurnConflictException.class)
    public ProblemDetail chatConflict(StoneChatTurnConflictException exception) {
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneChatRateLimitedException.class)
    public ProblemDetail chatRate(StoneChatRateLimitedException exception, HttpServletResponse response) {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfter()));
        return chatProblems.create(exception).problem();
    }
    @ExceptionHandler(StoneChatConversationBusyException.class)
    public ProblemDetail chatBusy(StoneChatConversationBusyException exception, HttpServletResponse response) {
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        return chatProblems.create(exception).problem();
    }
    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("about:blank"));
        return problemDetail;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body == null) body = createProblemDetail(exception, status, "Invalid request.", null, null, request);
        if (body instanceof ProblemDetail problemDetail && problemDetail.getType() == null) {
            problemDetail.setType(URI.create("about:blank"));
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }
}
