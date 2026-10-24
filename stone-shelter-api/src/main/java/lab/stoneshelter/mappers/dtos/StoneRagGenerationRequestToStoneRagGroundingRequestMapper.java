package lab.stoneshelter.mappers.dtos;

import java.util.List;
import lab.stoneshelter.shared.StoneRagGenerationRequest;
import lab.stoneshelter.shared.StoneRagGroundingRequest;
import lab.stoneshelter.shared.StoneRagSourceRequest;
import org.springframework.stereotype.Component;

@Component
public class StoneRagGenerationRequestToStoneRagGroundingRequestMapper extends AbstractDtoMapper<StoneRagGenerationRequestToStoneRagGroundingRequestMapper.Input, StoneRagGroundingRequest> {
    public record Input(StoneRagGenerationRequest facts, String prose, Long stoneId, List<StoneRagSourceRequest> sources) {}
    @Override protected StoneRagGroundingRequest mapToDto(Input source) {
        StoneRagGenerationRequest facts = new StoneRagGenerationRequest(); facts.setMode(source.facts().getMode()); facts.setQuestion(source.facts().getQuestion());
        facts.setReference(source.facts().getReference()); facts.setSelectionMode(source.facts().getSelectionMode()); facts.setDeadline(source.facts().getDeadline());
        facts.setCandidates(source.stoneId() == null ? source.facts().getCandidates() : source.facts().getCandidates().stream().filter(stone -> stone.getId() == source.stoneId()).toList());
        facts.setSources(source.sources());
        return new StoneRagGroundingRequest(facts, source.prose(), source.stoneId(), source.facts().getDeadline());
    }
}
