package lab.stoneshelter.shared;

import java.util.List;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.AdoptionStatus;

public record StoneExactSearchFilters(List<StoneType> stoneTypes, List<StoneSize> stoneSizes,
        List<AdoptionStatus> availability, List<StoneAdmissionDateRange> admissionDateRanges) {}
