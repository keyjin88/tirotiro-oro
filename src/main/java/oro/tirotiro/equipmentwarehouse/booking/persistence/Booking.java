package oro.tirotiro.equipmentwarehouse.booking.persistence;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import oro.tirotiro.equipmentwarehouse.auth.persistence.User;
import oro.tirotiro.equipmentwarehouse.persistence.AuditedEntity;

@Entity
@Table(name = "bookings")
public class Booking extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookingLine> lines = new HashSet<>();

    protected Booking() {
    }

    public Booking(User user, Instant startsAt, Instant endsAt, BookingStatus status) {
        this.user = user;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void reschedule(Instant startsAt, Instant endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void replaceLines(Set<BookingLine> lines) {
        this.lines.clear();
        this.lines.addAll(lines);
    }

    public void cancel(User cancelledBy, String cancelReason, Instant cancelledAt) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelReason = cancelReason;
        this.cancelledAt = cancelledAt;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public User getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Set<BookingLine> getLines() {
        return lines;
    }
}
