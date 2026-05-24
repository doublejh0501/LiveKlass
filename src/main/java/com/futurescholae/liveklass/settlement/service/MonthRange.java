package com.futurescholae.liveklass.settlement.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;

/**
 * 월 경계 / 기간 경계 → UTC Instant 반열린 구간(`[from, toExclusive)`) 변환 유틸.
 *
 * 작업지시서 §3.3 정책:
 *   - 저장은 UTC `Instant`, 계산은 KST 기준으로 변환해서 처리한다.
 *   - "말일 23:59:59" 닫힘 비교 대신, 다음 달 시작 시각 미만(`< 다음달초`)으로 반열린 구간 비교.
 */
public final class MonthRange {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Instant fromInclusive;
    private final Instant toExclusive;

    private MonthRange(Instant fromInclusive, Instant toExclusive) {
        this.fromInclusive = fromInclusive;
        this.toExclusive = toExclusive;
    }

    public static MonthRange ofYearMonthKst(YearMonth ym) {
        Instant from = ym.atDay(1).atStartOfDay(KST).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(KST).toInstant();
        return new MonthRange(from, to);
    }

    /**
     * 운영자 집계 API용 — KST 기준 [from 00:00, to+1 00:00) 반열린 구간.
     */
    public static MonthRange ofDateRangeKst(LocalDate fromDateInclusive, LocalDate toDateInclusive) {
        Instant from = fromDateInclusive.atStartOfDay(KST).toInstant();
        Instant to = toDateInclusive.plusDays(1).atStartOfDay(KST).toInstant();
        return new MonthRange(from, to);
    }

    public Instant fromInclusive() {
        return fromInclusive;
    }

    public Instant toExclusive() {
        return toExclusive;
    }
}
