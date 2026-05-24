package com.futurescholae.liveklass.settlement.service;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementCalculatorTest {

    private static final BigDecimal RATE = new BigDecimal("0.20");

    private static SaleRecord sale(String id, long amount) {
        return SaleRecord.builder()
                .id(id)
                .courseId("course-x")
                .studentId("student-x")
                .amount(amount)
                .paidAt(Instant.parse("2025-03-01T00:00:00Z"))
                .build();
    }

    private static CancelRecord cancel(String id, String saleId, long refundAmount) {
        return CancelRecord.builder()
                .id(id)
                .saleId(saleId)
                .refundAmount(refundAmount)
                .cancelledAt(Instant.parse("2025-03-15T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("creator-1의 2025-03 검증표 수치를 정확히 산출한다")
    void should_match_assignment_validation_table_for_creator1_march() {
        List<SaleRecord> sales = List.of(
                sale("sale-1", 50_000),
                sale("sale-2", 50_000),
                sale("sale-3", 80_000),
                sale("sale-4", 80_000)
        );
        List<CancelRecord> cancels = List.of(
                cancel("cancel-1", "sale-3", 80_000),
                cancel("cancel-2", "sale-4", 30_000)
        );

        SettlementResult r = SettlementCalculator.calculate(sales, cancels, RATE);

        assertThat(r.totalSalesAmount()).isEqualTo(260_000);
        assertThat(r.totalRefundAmount()).isEqualTo(110_000);
        assertThat(r.netSalesAmount()).isEqualTo(150_000);
        assertThat(r.commissionAmount()).isEqualTo(30_000);
        assertThat(r.payoutAmount()).isEqualTo(120_000);
        assertThat(r.saleCount()).isEqualTo(4);
        assertThat(r.cancelCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("판매/취소가 모두 없으면 모든 금액이 0이고 건수도 0이다")
    void should_return_zero_for_month_with_no_records() {
        SettlementResult r = SettlementCalculator.calculate(List.of(), List.of(), RATE);

        assertThat(r.totalSalesAmount()).isZero();
        assertThat(r.totalRefundAmount()).isZero();
        assertThat(r.netSalesAmount()).isZero();
        assertThat(r.commissionAmount()).isZero();
        assertThat(r.payoutAmount()).isZero();
        assertThat(r.saleCount()).isZero();
        assertThat(r.cancelCount()).isZero();
    }

    @Test
    @DisplayName("판매 없이 환불만 존재하면 순판매는 음수, 수수료는 0(클램프), payout=net (수수료 비환불 정책)")
    void should_calculate_negative_payout_when_only_refund_exists() {
        List<CancelRecord> cancels = List.of(cancel("cancel-3", "sale-5", 60_000));

        SettlementResult r = SettlementCalculator.calculate(List.of(), cancels, RATE);

        assertThat(r.totalSalesAmount()).isZero();
        assertThat(r.totalRefundAmount()).isEqualTo(60_000);
        assertThat(r.netSalesAmount()).isEqualTo(-60_000);
        assertThat(r.commissionAmount()).isZero();
        assertThat(r.payoutAmount()).isEqualTo(-60_000);
        assertThat(r.saleCount()).isZero();
        assertThat(r.cancelCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("부분 환불은 순판매에 정확히 반영된다 (원결제 80,000 / 환불 30,000)")
    void should_reflect_partial_refund_in_net_sales() {
        List<SaleRecord> sales = List.of(sale("sale-4", 80_000));
        List<CancelRecord> cancels = List.of(cancel("cancel-2", "sale-4", 30_000));

        SettlementResult r = SettlementCalculator.calculate(sales, cancels, RATE);

        assertThat(r.netSalesAmount()).isEqualTo(50_000);
        assertThat(r.commissionAmount()).isEqualTo(10_000);
        assertThat(r.payoutAmount()).isEqualTo(40_000);
    }

    @Test
    @DisplayName("동일 월 다수 취소는 환불 합으로 정확히 합산된다")
    void should_sum_multiple_cancels_in_same_month() {
        List<SaleRecord> sales = List.of(sale("sale-a", 100_000), sale("sale-b", 100_000));
        List<CancelRecord> cancels = List.of(
                cancel("cancel-a1", "sale-a", 40_000),
                cancel("cancel-a2", "sale-a", 30_000),
                cancel("cancel-b1", "sale-b", 50_000)
        );

        SettlementResult r = SettlementCalculator.calculate(sales, cancels, RATE);

        assertThat(r.totalRefundAmount()).isEqualTo(120_000);
        assertThat(r.netSalesAmount()).isEqualTo(80_000);
        assertThat(r.commissionAmount()).isEqualTo(16_000);
        assertThat(r.payoutAmount()).isEqualTo(64_000);
        assertThat(r.cancelCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("수수료는 원 단위로 절사(floor)되며 payout 합산 검산이 성립한다")
    void should_floor_commission_and_preserve_payout_invariant() {
        // 999 * 0.20 = 199.8 → 199 (floor)
        List<SaleRecord> sales = List.of(sale("sale-x", 999));

        SettlementResult r = SettlementCalculator.calculate(sales, List.of(), RATE);

        assertThat(r.netSalesAmount()).isEqualTo(999);
        assertThat(r.commissionAmount()).isEqualTo(199);
        assertThat(r.payoutAmount()).isEqualTo(800);
        // 합계 검산: net == commission + payout
        assertThat(r.commissionAmount() + r.payoutAmount()).isEqualTo(r.netSalesAmount());
    }

    @Test
    @DisplayName("수수료율은 주입 가능하다 (변경 가능성을 설계에 반영)")
    void should_accept_pluggable_commission_rate() {
        List<SaleRecord> sales = List.of(sale("sale-x", 100_000));
        BigDecimal newRate = new BigDecimal("0.15");

        SettlementResult r = SettlementCalculator.calculate(sales, List.of(), newRate);

        assertThat(r.commissionRate()).isEqualByComparingTo(newRate);
        assertThat(r.commissionAmount()).isEqualTo(15_000);
        assertThat(r.payoutAmount()).isEqualTo(85_000);
    }
}
