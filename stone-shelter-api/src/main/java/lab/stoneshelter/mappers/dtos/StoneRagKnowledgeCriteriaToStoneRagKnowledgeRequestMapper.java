package lab.stoneshelter.mappers.dtos;

import java.time.Instant;
import lab.stoneshelter.criteria.StoneRagKnowledgeCriteria;
import lab.stoneshelter.shared.StoneRagKnowledgeRequest;
import org.springframework.stereotype.Component;

@Component
public class StoneRagKnowledgeCriteriaToStoneRagKnowledgeRequestMapper extends AbstractDtoMapper<StoneRagKnowledgeCriteriaToStoneRagKnowledgeRequestMapper.Input, StoneRagKnowledgeRequest> {
    public record Input(StoneRagKnowledgeCriteria criteria, Instant deadline) {}
    @Override protected StoneRagKnowledgeRequest mapToDto(Input source) { return new StoneRagKnowledgeRequest(source.criteria(), source.deadline()); }
}
