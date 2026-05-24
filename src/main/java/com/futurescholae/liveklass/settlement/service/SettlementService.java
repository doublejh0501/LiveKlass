package com.futurescholae.liveklass.settlement.service;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import com.futurescholae.liveklass.settlement.domain.SettlementSnapshot;
import com.futurescholae.liveklass.settlement.exception.BusinessException;
import com.futurescholae.liveklass.settlement.exception.ErrorCode;
import com.futurescholae.liveklass.settlement.repository.CancelRecordRepository;
import com.futurescholae.liveklass.settlement.repository.CourseRepository;
import com.futurescholae.liveklass.settlement.repository.SaleRecordRepository;
import com.futurescholae.liveklass.settlement.repository.SettlementSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 정산 조회 / 확정 / 지급 처리.
 *
 * 조회(Service) ↔ 계산(Calculator) 분리:
 *   - 본 클래스는 DB에서 필요한 판매/취소를 모은다.
 *   - 실제 합산·수수료·payout 계산은 {@link SettlementCalculator}에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SaleRecordRepository saleRecordRepository;
    private final CancelRecordRepository cancelRecordRepository;
    private final CourseRepository courseRepository;
    private final SettlementSnapshotRepository settlementSnapshotRepository;
    private final CommissionRateProvider commissionRateProvider;

    @Transactional(readOnly = true)
    public SettlementResult calculateMonthly(String creatorId, YearMonth yearMonth) {
        MonthRange range = MonthRange.ofYearMonthKst(yearMonth);

        List<SaleRecord> sales = saleRecordRepository.findByCreatorAndPaidAtRange(
                creatorId, range.fromInclusive(), range.toExclusive());
        List<CancelRecord> cancels = cancelRecordRepository.findByCreatorAndCancelledAtRange(
                creatorId, range.fromInclusive(), range.toExclusive());

        // 수수료율 적용 정책: "정산월 기준 단일 율"
        //   - 환불은 §3.1에 따라 취소월에 귀속되므로, 1월 판매를 2월 취소해도 환불은 2월 정산에 잡힌다.
        //   - 그 환불에 어떤 율을 적용할지는 명세가 침묵하는 underspecified 지점.
        //   - 본 구현은 "정산이 발생한 월의 율"(= 정산 월 1일 기준)을 단일 율로 적용한다.
        //     이렇게 하면 한 정산 응답 내 모든 라인이 동일한 율로 일관되게 계산된다.
        return SettlementCalculator.calculate(
                sales, cancels,
                commissionRateProvider.rateAt(yearMonth.atDay(1)));
    }

    /**
     * 운영자 집계: 기간 [from, to] (KST, 양 끝 포함) 내에서 크리에이터별 정산 집계.
     * 음수 payout 크리에이터도 합계에 그대로 포함(작업지시서 §3.2 정책: 차감 정산).
     */
    @Transactional(readOnly = true)
    public AdminAggregateResult aggregate(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    "`to` must be on or after `from`");
        }
        MonthRange range = MonthRange.ofDateRangeKst(from, to);

        List<SaleRecord> sales = saleRecordRepository.findByPaidAtRange(
                range.fromInclusive(), range.toExclusive());
        List<CancelRecord> cancels = cancelRecordRepository.findByCancelledAtRange(
                range.fromInclusive(), range.toExclusive());

        Map<String, String> courseToCreator = new HashMap<>();
        courseRepository.findAll().forEach(c -> courseToCreator.put(c.getId(), c.getCreatorId()));

        Map<String, List<SaleRecord>> salesByCreator = new HashMap<>();
        Map<String, List<CancelRecord>> cancelsByCreator = new HashMap<>();
        sales.forEach(s -> salesByCreator
                .computeIfAbsent(courseToCreator.get(s.getCourseId()), k -> new java.util.ArrayList<>())
                .add(s));

        // 취소는 sale → course → creator 매핑이 필요. saleId → creatorId 매핑을 미리 만든다.
        Map<String, String> saleToCreator = new HashMap<>();
        saleRecordRepository.findAllById(cancels.stream().map(CancelRecord::getSaleId).toList())
                .forEach(s -> saleToCreator.put(s.getId(), courseToCreator.get(s.getCourseId())));
        cancels.forEach(cr -> cancelsByCreator
                .computeIfAbsent(saleToCreator.get(cr.getSaleId()), k -> new java.util.ArrayList<>())
                .add(cr));

        // creator 정렬 (운영자 가독성).
        Map<String, AdminAggregateResult.CreatorRow> rows = new TreeMap<>();
        java.util.Set<String> creators = new java.util.HashSet<>();
        creators.addAll(salesByCreator.keySet());
        creators.addAll(cancelsByCreator.keySet());

        // 가정: 집계 기간 내 단일 수수료율 적용 — 율 변경 시점을 걸치는 기간 집계는 본 구현 범위 밖.
        // (수수료율 이력 도입 시 이 한 줄을 "기간 분할 + 분할별 율 매칭" 로직으로 교체하면 된다.)
        java.math.BigDecimal periodRate = commissionRateProvider.rateAt(from);

        long totalPayout = 0L;
        for (String creatorId : creators) {
            if (creatorId == null) continue;
            List<SaleRecord> cs = salesByCreator.getOrDefault(creatorId, List.of());
            List<CancelRecord> cc = cancelsByCreator.getOrDefault(creatorId, List.of());
            SettlementResult r = SettlementCalculator.calculate(cs, cc, periodRate);
            rows.put(creatorId, new AdminAggregateResult.CreatorRow(
                    creatorId,
                    r.totalSalesAmount(),
                    r.totalRefundAmount(),
                    r.netSalesAmount(),
                    r.commissionAmount(),
                    r.payoutAmount(),
                    r.saleCount(),
                    r.cancelCount()
            ));
            totalPayout += r.payoutAmount();
        }
        return new AdminAggregateResult(from, to, List.copyOf(rows.values()), totalPayout);
    }

    /**
     * 가산점 — 정산 확정 (PENDING → CONFIRMED, 동일 기간 재확정 시 409).
     */
    @Transactional
    public SettlementSnapshot confirm(String creatorId, YearMonth yearMonth) {
        SettlementResult result = calculateMonthly(creatorId, yearMonth);

        if (settlementSnapshotRepository.existsByCreatorIdAndPeriodYearMonth(creatorId, yearMonth.toString())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SETTLEMENT,
                    "Settlement already exists for " + creatorId + " / " + yearMonth);
        }

        SettlementSnapshot snapshot = SettlementSnapshot.confirmed(
                creatorId, yearMonth, result.payoutAmount(), Instant.now());
        try {
            return settlementSnapshotRepository.save(snapshot);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_SETTLEMENT,
                    "Settlement already exists for " + creatorId + " / " + yearMonth);
        }
    }

    /**
     * 가산점 — 정산 지급 (CONFIRMED → PAID).
     */
    @Transactional
    public SettlementSnapshot markPaid(Long snapshotId) {
        SettlementSnapshot snapshot = settlementSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND,
                        "Settlement snapshot not found: " + snapshotId));
        try {
            snapshot.markPaid(Instant.now());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATE, e.getMessage());
        }
        return snapshot;
    }
}
