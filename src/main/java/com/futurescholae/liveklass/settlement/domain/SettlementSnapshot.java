package com.futurescholae.liveklass.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.YearMonth;

@Entity
@Table(
        name = "settlement_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_settlement_creator_period",
                columnNames = {"creator_id", "period_year_month"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private String creatorId;

    @Column(name = "period_year_month", nullable = false, length = 7)
    private String periodYearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementStatus status;

    @Column(name = "settled_amount", nullable = false)
    private long settledAmount;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public static SettlementSnapshot confirmed(String creatorId, YearMonth period, long settledAmount, Instant now) {
        return SettlementSnapshot.builder()
                .creatorId(creatorId)
                .periodYearMonth(period.toString())
                .status(SettlementStatus.CONFIRMED)
                .settledAmount(settledAmount)
                .confirmedAt(now)
                .build();
    }

    public void markPaid(Instant now) {
        if (this.status != SettlementStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED snapshots can be marked as PAID. current=" + this.status);
        }
        this.status = SettlementStatus.PAID;
        this.paidAt = now;
    }
}
