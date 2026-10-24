package lab.stoneshelter.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lab.stoneshelter.enums.AdoptionStatus;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;

@Entity
@Table(name = "stone")
public class StoneEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "storage_uuid", nullable = false, unique = true)
    private UUID storageUuid = UUID.randomUUID();
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "photo_url", length = 500) private String photo;
    @Enumerated(EnumType.STRING) @Column(name = "stone_type", nullable = false, length = 32)
    private StoneType stoneType;
    @Column(length = 2048) private String biography;
    @Enumerated(EnumType.STRING) @Column(name = "adoption_status", nullable = false, length = 16)
    private AdoptionStatus adoptionStatus;
    @Column(name = "admission_date", nullable = false) private Instant admissionDate;
    @Enumerated(EnumType.STRING) @Column(name = "size", nullable = false, length = 16)
    private StoneSize stoneSize;

    @BatchSize(size = 24)
    @OneToMany(mappedBy = "stone")
    @OrderBy("position ASC")
    private List<StonePhotoEntity> photos = new ArrayList<>();

    public List<StonePhotoEntity> getPhotos() { return photos; }
    public void setPhotos(List<StonePhotoEntity> photos) { this.photos = photos; }

    public StoneEntity() {}

    public UUID getStorageUuid() { return storageUuid; }
    public void setStorageUuid(UUID storageUuid) { this.storageUuid = storageUuid; }

    public void setId(Long id) { this.id = id; }

    public Long getId() {
        return id;
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
