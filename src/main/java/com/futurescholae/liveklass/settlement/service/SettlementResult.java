package com.futurescholae.liveklass.settlement.service;

import java.math.BigDecimal;

public record SettlementResult(
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        BigDecimal commissionRate,
        long commissionAmount,
        long payoutAmount,
        int saleCount,
        int cancelCount
) {
}
