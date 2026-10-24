package lab.stoneshelter.criteria;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.AdoptionStatus;

public class StoneExactSearchCriteria {
    public record DateRange(Instant startInclusive, Instant endExclusive) {}
    private List<StoneType> stoneTypes = List.of();
    private List<StoneSize> stoneSizes = List.of();
    private List<AdoptionStatus> availability = List.of();
    private List<DateRange> admissionDateRanges = List.of();
    private Long stoneId;
    public StoneExactSearchCriteria() {}
    public List<StoneType> getStoneTypes() { return stoneTypes; }
    public void setStoneTypes(List<StoneType> value) { stoneTypes = List.copyOf(value); }
    public List<StoneSize> getStoneSizes() { return stoneSizes; }
    public void setStoneSizes(List<StoneSize> value) { stoneSizes = List.copyOf(value); }
    public List<AdoptionStatus> getAvailability() { return availability; }
    public void setAvailability(List<AdoptionStatus> value) { availability = List.copyOf(value); }
    public List<DateRange> getAdmissionDateRanges() { return admissionDateRanges; }
    public void setAdmissionDateRanges(List<DateRange> value) { admissionDateRanges = List.copyOf(value); }
    public Long getStoneId() { return stoneId; }
    public void setStoneId(Long value) { stoneId = value; }
    public boolean hasNarrowing() { return stoneId != null || !stoneTypes.isEmpty() && Set.copyOf(stoneTypes).size() < StoneType.values().length
            || !stoneSizes.isEmpty() && Set.copyOf(stoneSizes).size() < StoneSize.values().length || !admissionDateRanges.isEmpty(); }
}
