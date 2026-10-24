package lab.stoneshelter.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Stone details for creating a catalog entry.")
@JsonIgnoreProperties("admissionDate")
public class StoneCreateRequest {
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
    @NotNull
    @Schema(description = "Stone size.")
    private StoneSize stoneSize;

    @Size(max = 16)
    @Schema(description = "Ordered distinct draft photograph UUIDs for the initial gallery; optional.")
    private List<@NotNull UUID> photoUploadIds;

    public List<UUID> getPhotoUploadIds() { return photoUploadIds; }
    public void setPhotoUploadIds(List<UUID> photoUploadIds) { this.photoUploadIds = photoUploadIds; }
    public StoneCreateRequest() {}

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

    public StoneSize getStoneSize() {
        return stoneSize;
    }

    public void setStoneSize(StoneSize stoneSize) {
        this.stoneSize = stoneSize;
    }
}
