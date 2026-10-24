package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;

@Schema(description = "Updated stone catalog entry.")
public class StoneUpdateResponse {
    @Schema(description = "Unique stone identifier.")
    private Long id;
    @Schema(description = "Stone name.")
    private String name;
    @Schema(description = "Optional photo value, preserved as supplied.")
    private String photo;
    @Schema(description = "Stone type.")
    private StoneType stoneType;
    @Schema(description = "Optional stone biography.")
    private String biography;
    @Schema(description = "Stone adoption status.")
    private AdoptionStatus adoptionStatus;
    @Schema(description = "Stone admission timestamp.")
    private Instant admissionDate;
    @Schema(description = "Stone size.")
    private StoneSize stoneSize;

    public StoneUpdateResponse() {}

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
