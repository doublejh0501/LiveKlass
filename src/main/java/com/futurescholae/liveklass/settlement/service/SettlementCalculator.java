package com.futurescholae.liveklass.settlement.service;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 정산 계산 순수 도메인 컴포넌트.
 * Spring / Repository / JPA 의존 0. POJO 단위 테스트로 정책 전체를 증명한다.
 *
 * 정책 (작업지시서 §3 참조):
 *   - netSales = totalSales - totalRefund (음수 허용 — 환불만 있는 달은 음수 그대로 노출)
 *   - commission = floor( max(netSales, 0) * rate )  // 수수료 비환불(non-refundable) 정책
 *   - payout = netSales - commission
 *   - 합계 검산이 깨지지 않도록 payout 은 (netSales - commission) 으로 도출(독립 계산 금지).
 */
public final class SettlementCalculator {

    private SettlementCalculator() {
    }

    public static SettlementResult calculate(
            List<SaleRecord> sales,
            List<CancelRecord> cancels,
            BigDecimal commissionRate
    ) {
        Objects.requireNonNull(sales, "sales");
        Objects.requireNonNull(cancels, "cancels");
        Objects.requireNonNull(commissionRate, "commissionRate");

        long totalSales = sales.stream().mapToLong(SaleRecord::getAmount).sum();
        long totalRefund = cancels.stream().mapToLong(CancelRecord::getRefundAmount).sum();
        long netSales = totalSales - totalRefund;

        long positiveNet = Math.max(netSales, 0L);
        long commission = BigDecimal.valueOf(positiveNet)
                .multiply(commissionRate)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
        long payout = netSales - commission;

        return new SettlementResult(
                totalSales,
                totalRefund,
                netSales,
                commissionRate,
                commission,
                payout,
                sales.size(),
                cancels.size()
        );
    }
}
