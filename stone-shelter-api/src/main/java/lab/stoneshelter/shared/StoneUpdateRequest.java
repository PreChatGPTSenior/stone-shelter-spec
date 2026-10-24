package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

@Schema(description = "Replacement details for an existing catalog entry.")
public class StoneUpdateRequest {
    @NotBlank @Size(max = 120)
    @Schema(description = "Stone name.")
    private String name;
    @Size(max = 500)
    @Schema(description = "Optional photo value, preserved as supplied.")
    private String photo;
    @NotNull
    @Schema(description = "Stone type.")
    private StoneType stoneType;
    @Size(max = 2048)
    @Schema(description = "Optional stone biography.")
    private String biography;
    @NotNull
    @Schema(description = "Stone adoption status.")
    private AdoptionStatus adoptionStatus;
    @NotNull @PastOrPresent
    @Schema(description = "Admission timestamp; requests require a value in the past or present.")
    private Instant admissionDate;
    @NotNull
    @Schema(description = "Stone size.")
    private StoneSize stoneSize;

    public StoneUpdateRequest() {}

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
