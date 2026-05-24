package com.futurescholae.liveklass.settlement.dto;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;

import java.time.Instant;

public record CancellationResponse(
        String id,
        String saleId,
        long refundAmount,
        Instant cancelledAt
) {
    public static CancellationResponse from(CancelRecord c) {
        return new CancellationResponse(c.getId(), c.getSaleId(), c.getRefundAmount(), c.getCancelledAt());
    }
}
