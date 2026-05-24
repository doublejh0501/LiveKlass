package com.futurescholae.liveklass.settlement.dto;

import com.futurescholae.liveklass.settlement.domain.SettlementSnapshot;
import com.futurescholae.liveklass.settlement.domain.SettlementStatus;

import java.time.Instant;

public record SettlementSnapshotResponse(
        Long id,
        String creatorId,
        String yearMonth,
        SettlementStatus status,
        long settledAmount,
        Instant confirmedAt,
        Instant paidAt
) {
    public static SettlementSnapshotResponse from(SettlementSnapshot s) {
        return new SettlementSnapshotResponse(
                s.getId(),
                s.getCreatorId(),
                s.getPeriodYearMonth(),
                s.getStatus(),
                s.getSettledAmount(),
                s.getConfirmedAt(),
                s.getPaidAt()
        );
    }
}
