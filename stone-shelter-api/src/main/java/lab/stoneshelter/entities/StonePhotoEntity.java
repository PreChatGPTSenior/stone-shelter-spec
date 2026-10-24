package lab.stoneshelter.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stone_photo")
public class StonePhotoEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stone_id", nullable = false)
    private StoneEntity stone;
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;
    @Column(nullable = false)
    private int position;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", unique = true)
    private StonePhotoDraftEntity draft;
    @Column(name = "copy_ready", nullable = false)
    private boolean copyReady = true;

    public StonePhotoDraftEntity getDraft() { return draft; }
    public void setDraft(StonePhotoDraftEntity draft) { this.draft = draft; }
    public boolean isCopyReady() { return copyReady; }
    public void setCopyReady(boolean copyReady) { this.copyReady = copyReady; }
    public StonePhotoEntity() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public StoneEntity getStone() { return stone; }
    public void setStone(StoneEntity stone) { this.stone = stone; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
