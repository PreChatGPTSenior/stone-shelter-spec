package lab.stoneshelter.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lab.stoneshelter.enums.PhotoStorageBucket;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "photo_cleanup")
@IdClass(PhotoCleanupId.class)
public class PhotoCleanupEntity {
    @Id @Column(name = "object_key", length = 500)
    private String objectKey;
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Id @Enumerated(EnumType.STRING) @Column(name = "bucket_type", nullable = false, length = 16)
    private PhotoStorageBucket bucketType = PhotoStorageBucket.PERMANENT;

    public PhotoStorageBucket getBucketType() { return bucketType; }
    public void setBucketType(PhotoStorageBucket bucketType) { this.bucketType = bucketType; }
    public PhotoCleanupEntity() {}
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }
}
