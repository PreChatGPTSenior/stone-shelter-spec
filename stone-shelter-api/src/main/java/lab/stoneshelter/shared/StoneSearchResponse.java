package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;

@Schema(description = "Stone catalog entry returned in search results.")
public class StoneSearchResponse {
    @Schema(description = "Unique stone identifier.")
    private Long id;
    @Schema(description = "Stone name.")
    private String name;
    @Schema(description = "First managed gallery URL, otherwise the unchanged optional legacy photo value.")
    private String photo;
    @Schema(description = "Stone type.")
    private StoneType stoneType;
    @Schema(description = "Optional stone biography.")
    private String biography;
    @Schema(description = "Stone adoption status.")
    private AdoptionStatus adoptionStatus;
    @Schema(description = "UTC admission timestamp; assigned by the backend during creation.")
    private Instant admissionDate;
    @Schema(description = "Stone size.")
    private StoneSize stoneSize;

    public StoneSearchResponse() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public StoneType getStoneType() {
        return stoneType;
    }

    public void setStoneType(StoneType stoneType) {
        this.stoneType = stoneType;
    }

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
    }

    public AdoptionStatus getAdoptionStatus() {
        return adoptionStatus;
    }

    public void setAdoptionStatus(AdoptionStatus adoptionStatus) {
        this.adoptionStatus = adoptionStatus;
    }

    public Instant getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(Instant admissionDate) {
        this.admissionDate = admissionDate;
    }

    public StoneSize getStoneSize() {
        return stoneSize;
    }

    public void setStoneSize(StoneSize stoneSize) {
        this.stoneSize = stoneSize;
    }
}
