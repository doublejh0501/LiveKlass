package com.futurescholae.liveklass.settlement.service;

import java.time.LocalDate;
import java.util.List;

public record AdminAggregateResult(
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
}
