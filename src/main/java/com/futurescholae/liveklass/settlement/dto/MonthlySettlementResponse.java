package com.futurescholae.liveklass.settlement.dto;

import com.futurescholae.liveklass.settlement.service.SettlementResult;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlySettlementResponse(
        String creatorId,
        String yearMonth,
        long totalSalesAmount,
        long totalRefundAmount,
        long netSalesAmount,
        BigDecimal commissionRate,
        long commissionAmount,
        long payoutAmount,
        int saleCount,
        int cancelCount
) {
    public static MonthlySettlementResponse of(String creatorId, YearMonth ym, SettlementResult r) {
        return new MonthlySettlementResponse(
                creatorId,
                ym.toString(),
                r.totalSalesAmount(),
                r.totalRefundAmount(),
                r.netSalesAmount(),
                r.commissionRate(),
                r.commissionAmount(),
                r.payoutAmount(),
                r.saleCount(),
                r.cancelCount()
        );
    }
}
