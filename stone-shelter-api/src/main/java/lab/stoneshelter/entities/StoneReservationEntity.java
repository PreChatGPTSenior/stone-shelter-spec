package lab.stoneshelter.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stone_reservation")
public class StoneReservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stone_id", nullable = false, unique = true)
    private StoneEntity stone;

    @Column(name = "applicant_name", nullable = false, columnDefinition = "text")
    private String applicantName;

    @Column(name = "contact_details", nullable = false, columnDefinition = "text")
    private String contactDetails;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public StoneReservationEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public StoneEntity getStone() { return stone; }
    public void setStone(StoneEntity stone) { this.stone = stone; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getContactDetails() { return contactDetails; }
    public void setContactDetails(String contactDetails) { this.contactDetails = contactDetails; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
