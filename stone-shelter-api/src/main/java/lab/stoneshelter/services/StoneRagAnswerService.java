package lab.stoneshelter.services;

import java.time.Clock;
import lab.stoneshelter.entities.StoneRagAnswerResultEntity;
import lab.stoneshelter.entities.StoneRagExecutionEntity;
import lab.stoneshelter.enums.StoneRagCompletionKind;
import lab.stoneshelter.enums.StoneRagMode;
import lab.stoneshelter.exceptions.InvalidStoneRagOutputException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import lab.stoneshelter.exceptions.StoneRagAnswerLimitExceededException;
import lab.stoneshelter.mappers.dtos.StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import lab.stoneshelter.shared.StoneRagAnswerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class StoneRagAnswerService {
    private record Components(StoneRagAnswerProvider provider, StoneRagAnswerValidationService validation,
            StoneRagGroundingService grounding, StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper mapping) {}
    private final Components components;
    private final StoneRagSettings settings;
    private final Clock clock;
    public StoneRagAnswerService(StoneRagAnswerProvider provider, StoneRagAnswerValidationService validation, StoneRagGroundingService grounding,
            StoneRagAnswerResultEntityToStoneRagAnswerResponseMapper mapping, StoneRagSettings settings, Clock clock) {
        components = new Components(provider, validation, grounding, mapping); this.settings = settings; this.clock = clock;
    }
    public StoneRagAnswerResponse generate(StoneRagGenerationRequest request, StoneRagExecutionEntity execution) { return components.mapping().toDto(prepare(request, execution)); }
    public StoneRagAnswerResultEntity prepare(StoneRagGenerationRequest request, StoneRagExecutionEntity execution) {
        if (request.getDeadline() == null || execution.getModelDeadline() == null || execution.getDeadline() == null)
            throw new IllegalArgumentException("Original turn/work deadlines are required");
        if (request.getDeadline().isAfter(execution.getModelDeadline())) request.setDeadline(execution.getModelDeadline());
        if (request.getMode() == StoneRagMode.SELECTION && request.getCandidates().isEmpty()) throw new IllegalArgumentException("Empty selection does not generate answers");
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                if (!clock.instant().isBefore(execution.getModelDeadline())) throw new StoneRagAnswerLimitExceededException();
                execution.consumeGenerationCall(settings.maximumGenerationCalls());
                String output = components.provider().invoke(request, attempt == 0 ? null
                        : "Explain every known ID in order, use permitted facts/wishes, keep missing individual properties unconfirmed, and show no sources.").text();
                try {
                    var answer = components.validation().validate(output, request);
                    if (!components.grounding().prepare(request, answer, execution).valid()) throw new InvalidStoneRagOutputException();
                    if (!clock.instant().isBefore(execution.getModelDeadline())) throw new StoneRagAnswerLimitExceededException();
                    return new StoneRagAnswerResultEntity(answer, StoneRagCompletionKind.GENERATED, null);
                } catch (InvalidStoneRagOutputException exception) { if (attempt == 1) throw exception; }
            }
            throw new InvalidStoneRagOutputException();
        } catch (RuntimeException exception) {
            if (request.getMode() == StoneRagMode.SELECTION) return new StoneRagAnswerResultEntity(null, StoneRagCompletionKind.TEMPLATE, exception);
            throw exception;
        }
    }
}
