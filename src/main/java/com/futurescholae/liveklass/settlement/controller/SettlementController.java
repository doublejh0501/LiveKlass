package com.futurescholae.liveklass.settlement.controller;

import com.futurescholae.liveklass.settlement.dto.MonthlySettlementResponse;
import com.futurescholae.liveklass.settlement.exception.BusinessException;
import com.futurescholae.liveklass.settlement.exception.ErrorCode;
import com.futurescholae.liveklass.settlement.service.SettlementResult;
import com.futurescholae.liveklass.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@RestController
@RequestMapping("/api/creators")
@RequiredArgsConstructor
public class SettlementController {

    private static final DateTimeFormatter YEAR_MONTH_STRICT =
            DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);

    private final SettlementService settlementService;

    @GetMapping("/{creatorId}/settlements")
    public MonthlySettlementResponse monthly(
            @PathVariable String creatorId,
            @RequestParam String yearMonth
    ) {
        YearMonth ym = parseYearMonthStrict(yearMonth);
        SettlementResult r = settlementService.calculateMonthly(creatorId, ym);
        return MonthlySettlementResponse.of(creatorId, ym, r);
    }

    /**
     * `2025-13`, `2025/03`, `25-3` 등은 400(INVALID_YEAR_MONTH)으로 거부.
     */
    private static YearMonth parseYearMonthStrict(String raw) {
        try {
            return YearMonth.parse(raw, YEAR_MONTH_STRICT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_YEAR_MONTH,
                    "Invalid yearMonth format. Expected `YYYY-MM`. given=" + raw);
        }
    }
}
