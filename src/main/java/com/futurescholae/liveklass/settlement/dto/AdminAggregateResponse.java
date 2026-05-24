package com.futurescholae.liveklass.settlement.dto;

import com.futurescholae.liveklass.settlement.service.AdminAggregateResult;

import java.time.LocalDate;
import java.util.List;

public record AdminAggregateResponse(
        LocalDate from,
        LocalDate to,
        List<CreatorRow> creators,
        long totalPayoutAmount
) {
    public record CreatorRow(
            String creatorId,
            long totalSalesAmount,
            long totalRefundAmount,
            long netSalesAmount,
            long commissionAmount,
            long payoutAmount,
            int saleCount,
            int cancelCount
    ) {
    }

    public static AdminAggregateResponse from(AdminAggregateResult r) {
        List<CreatorRow> rows = r.creators().stream()
                .map(c -> new CreatorRow(
                        c.creatorId(),
                        c.totalSalesAmount(), c.totalRefundAmount(), c.netSalesAmount(),
                        c.commissionAmount(), c.payoutAmount(),
                        c.saleCount(), c.cancelCount()))
                .toList();
        return new AdminAggregateResponse(r.from(), r.to(), rows, r.totalPayoutAmount());
    }
}
