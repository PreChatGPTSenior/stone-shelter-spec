package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;

@Schema(description = "Optional filters combined with AND within the AVAILABLE-only catalog; selections within each array use OR.")
public class StoneSearchFilter {
    @Schema(description = "Legacy exact stone type; AND with stoneTypes.")
    private StoneType stoneType;
    @Schema(description = "Legacy exact size; AND with stoneSizes.")
    private StoneSize stoneSize;
    @Schema(description = "Optional exact adoption status combined with mandatory AVAILABLE visibility; RESERVED or ADOPTED returns an empty page with totalElements 0.")
    private AdoptionStatus adoptionStatus;
    @Schema(description = "Optional sizes combined with OR; null or empty does not restrict results.")
    private List<@NotNull StoneSize> stoneSizes;
    @Schema(description = "Optional types combined with OR; null or empty does not restrict results.")
    private List<@NotNull StoneType> stoneTypes;
    @Schema(description = "Optional YYYY-MM-DD inclusive UTC lower day.")
    private LocalDate admissionDateFrom;
    @Schema(description = "Optional YYYY-MM-DD inclusive UTC upper day; must not precede admissionDateFrom.")
    private LocalDate admissionDateTo;

    public StoneSearchFilter() {}

    public StoneType getStoneType() { return stoneType; }

    public void setStoneType(StoneType stoneType) { this.stoneType = stoneType; }

    public StoneSize getStoneSize() { return stoneSize; }

    public void setStoneSize(StoneSize stoneSize) { this.stoneSize = stoneSize; }

    public AdoptionStatus getAdoptionStatus() { return adoptionStatus; }

    public void setAdoptionStatus(AdoptionStatus adoptionStatus) { this.adoptionStatus = adoptionStatus; }

    public List<StoneSize> getStoneSizes() { return stoneSizes; }

    public void setStoneSizes(List<StoneSize> stoneSizes) { this.stoneSizes = stoneSizes; }

    public List<StoneType> getStoneTypes() { return stoneTypes; }

    public void setStoneTypes(List<StoneType> stoneTypes) { this.stoneTypes = stoneTypes; }

    public LocalDate getAdmissionDateFrom() { return admissionDateFrom; }

    public void setAdmissionDateFrom(LocalDate admissionDateFrom) { this.admissionDateFrom = admissionDateFrom; }

    public LocalDate getAdmissionDateTo() { return admissionDateTo; }

    public void setAdmissionDateTo(LocalDate admissionDateTo) { this.admissionDateTo = admissionDateTo; }
}
