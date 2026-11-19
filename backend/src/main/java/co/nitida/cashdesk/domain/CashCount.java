package co.nitida.cashdesk.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.CopAmount;
import co.nitida.shared.Texts;
import co.nitida.shared.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** RN-52: expected cash vs counted cash for one Bogota day. A closed count is never edited, only reopened. */
@Entity
@Table(schema = "cashdesk", name = "cash_count")
public class CashCount extends AssignedIdEntity {
    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;
    @Column(name = "count_day", nullable = false)
    private LocalDate countDay;
    @Column(nullable = false)
    private long expected;
    private Long counted;
    private Long difference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashCountStatus status = CashCountStatus.OPEN;
    @Column(name = "closed_by")
    private UUID closedBy;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "reopen_reason")
    private String reopenReason;
    @Version
    private long version;

    protected CashCount() {}

    public static CashCount close(UUID id, UUID workshopId, LocalDate day, long expected, long counted, long tolerance, UUID by, Instant now) {
        var c = new CashCount();
        c.id = id;
        c.workshopId = workshopId;
        c.countDay = day;
        c.apply(expected, counted, tolerance, by, now);
        return c;
    }

    /** Closes a count that was reopened. */
    public void recount(long expected, long counted, long tolerance, UUID by, Instant now) {
        if (status != CashCountStatus.OPEN) {
            throw ApiException.conflict("CASH_COUNT_CLOSED", "El cuadre del día ya está cerrado: reábralo con un motivo para corregirlo");
        }
        apply(expected, counted, tolerance, by, now);
    }

    private void apply(long expected, long counted, long tolerance, UUID by, Instant now) {
        this.expected = expected;
        this.counted = new CopAmount(counted).value();
        this.difference = counted - expected;
        this.status = Math.abs(difference) <= tolerance ? CashCountStatus.BALANCED : CashCountStatus.UNBALANCED;
        this.closedBy = by;
        this.closedAt = now;
    }

    /** Returns this so callers can chain; reopening an open count is a conflict. */
    public CashCount reopen(String reason, Instant now) {
        String r = Texts.requireLength(reason, 10, 500, "REASON_REQUIRED", "Indique el motivo (10 a 500 caracteres)");
        if (status == CashCountStatus.OPEN) throw ApiException.conflict("CASH_COUNT_NOT_CLOSED", "El cuadre del día ya está abierto");
        status = CashCountStatus.OPEN;
        reopenReason = r;
        closedAt = null;
        return this;
    }

    public UUID getWorkshopId() { return workshopId; }
    public LocalDate getCountDay() { return countDay; }
    public long getExpected() { return expected; }
    public Long getCounted() { return counted; }
    public Long getDifference() { return difference; }
    public CashCountStatus getStatus() { return status; }
    public Instant getClosedAt() { return closedAt; }
    public String getReopenReason() { return reopenReason; }
}
