package lab.stoneshelter.shared;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lab.stoneshelter.enums.StoneCardReindexTarget;

public record StoneCardReindexStartRequest(@NotNull StoneCardReindexTarget target, List<@NotNull @Positive Long> stoneIds) {}
