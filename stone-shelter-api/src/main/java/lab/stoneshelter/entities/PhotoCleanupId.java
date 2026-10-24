package lab.stoneshelter.entities;

import java.io.Serializable;
import java.util.Objects;
import lab.stoneshelter.enums.PhotoStorageBucket;

public class PhotoCleanupId implements Serializable {
    private String objectKey;
    private PhotoStorageBucket bucketType;

    public PhotoCleanupId() {}
    public PhotoCleanupId(String objectKey, PhotoStorageBucket bucketType) {
        this.objectKey = objectKey;
        this.bucketType = bucketType;
    }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public PhotoStorageBucket getBucketType() { return bucketType; }
    public void setBucketType(PhotoStorageBucket bucketType) { this.bucketType = bucketType; }
    @Override public boolean equals(Object other) {
        return other instanceof PhotoCleanupId photoCleanupId
                && Objects.equals(objectKey, photoCleanupId.objectKey) && bucketType == photoCleanupId.bucketType;
    }
    @Override public int hashCode() { return Objects.hash(objectKey, bucketType); }
}
