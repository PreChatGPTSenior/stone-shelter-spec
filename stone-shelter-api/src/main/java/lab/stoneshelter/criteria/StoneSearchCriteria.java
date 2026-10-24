package lab.stoneshelter.criteria;

import java.time.Instant;
import java.util.List;
import lab.stoneshelter.enums.StoneSortField;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.AdoptionStatus;

public class StoneSearchCriteria {
    private List<StoneType> stoneTypes;
    private List<StoneSize> stoneSizes;
    private Instant admissionDateFrom;
    private Instant admissionDateToExclusive;
    private StoneType stoneType;
    private StoneSize stoneSize;
    private AdoptionStatus adoptionStatus;
    private int page;
    private int size;
    private StoneSortField field;
    private boolean descending;

    public StoneSearchCriteria() {}

    public StoneType getStoneType() {
        return stoneType;
    }

    public void setStoneType(StoneType stoneType) {
        this.stoneType = stoneType;
    }

    public StoneSize getStoneSize() {
        return stoneSize;
    }

    public void setStoneSize(StoneSize stoneSize) {
        this.stoneSize = stoneSize;
    }

    public AdoptionStatus getAdoptionStatus() {
        return adoptionStatus;
    }

    public void setAdoptionStatus(AdoptionStatus adoptionStatus) {
        this.adoptionStatus = adoptionStatus;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public StoneSortField getField() {
        return field;
    }

    public void setField(StoneSortField field) {
        this.field = field;
    }

    public boolean isDescending() {
        return descending;
    }

    public void setDescending(boolean descending) {
        this.descending = descending;
    }

    public List<StoneType> getStoneTypes() { return stoneTypes; }

    public void setStoneTypes(List<StoneType> stoneTypes) { this.stoneTypes = stoneTypes; }

    public List<StoneSize> getStoneSizes() { return stoneSizes; }

    public void setStoneSizes(List<StoneSize> stoneSizes) { this.stoneSizes = stoneSizes; }

    public Instant getAdmissionDateFrom() { return admissionDateFrom; }

    public void setAdmissionDateFrom(Instant admissionDateFrom) { this.admissionDateFrom = admissionDateFrom; }

    public Instant getAdmissionDateToExclusive() { return admissionDateToExclusive; }

    public void setAdmissionDateToExclusive(Instant admissionDateToExclusive) { this.admissionDateToExclusive = admissionDateToExclusive; }
}
