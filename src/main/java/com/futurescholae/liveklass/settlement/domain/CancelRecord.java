package com.futurescholae.liveklass.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "cancel_records", indexes = {
        @Index(name = "idx_cancel_sale", columnList = "sale_id"),
        @Index(name = "idx_cancel_cancelled_at", columnList = "cancelled_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CancelRecord {

    @Id
    private String id;

    @Column(name = "sale_id", nullable = false)
    private String saleId;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;
}
