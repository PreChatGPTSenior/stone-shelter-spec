package lab.stoneshelter.mappers.dtos;

import java.util.List;
import lab.stoneshelter.entities.StoneCardReindexJobEntity;
import lab.stoneshelter.shared.StoneCardReindexStatusResponse;
import lab.stoneshelter.shared.StoneCardReindexError;
import org.springframework.stereotype.Component;

@Component
public class StoneCardReindexJobEntityToStoneCardReindexStatusResponseMapper
        extends AbstractDtoMapper<StoneCardReindexJobEntity, StoneCardReindexStatusResponse> {
    @Override
    protected StoneCardReindexStatusResponse mapToDto(StoneCardReindexJobEntity stoneCardReindexJobEntity) {
        StoneCardReindexStatusResponse stoneCardReindexStatusResponse = new StoneCardReindexStatusResponse();
        stoneCardReindexStatusResponse.setJobId(stoneCardReindexJobEntity.getJobId());
        stoneCardReindexStatusResponse.setTarget(stoneCardReindexJobEntity.getTarget());
        stoneCardReindexStatusResponse.setStatus(stoneCardReindexJobEntity.getStatus());
        stoneCardReindexStatusResponse.setTotalCount(stoneCardReindexJobEntity.getTotalCount());
        stoneCardReindexStatusResponse.setProcessedCount(stoneCardReindexJobEntity.getProcessedCount());
        stoneCardReindexStatusResponse.setEnqueuedCount(stoneCardReindexJobEntity.getEnqueuedCount());
        stoneCardReindexStatusResponse.setSkippedCount(stoneCardReindexJobEntity.getSkippedCount());
        stoneCardReindexStatusResponse.setFailedCount(stoneCardReindexJobEntity.getFailedCount());
        stoneCardReindexStatusResponse.setReconciledCount(stoneCardReindexJobEntity.getReconciledCount());
        stoneCardReindexStatusResponse.setCreatedAt(stoneCardReindexJobEntity.getCreatedAt());
        stoneCardReindexStatusResponse.setUpdatedAt(stoneCardReindexJobEntity.getUpdatedAt());
        stoneCardReindexStatusResponse.setErrors(stoneCardReindexJobEntity.getErrors() == null ? List.of() : stoneCardReindexJobEntity.getErrors().stream().map(item -> new StoneCardReindexError(item.getStoneId(), item.getError())).toList());
        return stoneCardReindexStatusResponse;
    }
}
