package com.futurescholae.liveklass.settlement.controller;

import com.futurescholae.liveklass.settlement.dto.AdminAggregateResponse;
import com.futurescholae.liveklass.settlement.dto.ConfirmSettlementRequest;
import com.futurescholae.liveklass.settlement.dto.SettlementSnapshotResponse;
import com.futurescholae.liveklass.settlement.exception.BusinessException;
import com.futurescholae.liveklass.settlement.exception.ErrorCode;
import com.futurescholae.liveklass.settlement.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSettlementController {

    private static final DateTimeFormatter YEAR_MONTH_STRICT =
            DateTimeFormatter.ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);

    private final SettlementService settlementService;

    @GetMapping("/settlements")
    public AdminAggregateResponse aggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return AdminAggregateResponse.from(settlementService.aggregate(from, to));
    }

    @PostMapping("/settlements/confirm")
    public ResponseEntity<SettlementSnapshotResponse> confirm(@Valid @RequestBody ConfirmSettlementRequest req) {
        YearMonth ym = parseYearMonthStrict(req.yearMonth());
        return ResponseEntity.ok(
                SettlementSnapshotResponse.from(settlementService.confirm(req.creatorId(), ym))
        );
    }

    @PostMapping("/settlements/{id}/pay")
    public ResponseEntity<SettlementSnapshotResponse> pay(@PathVariable Long id) {
        return ResponseEntity.ok(SettlementSnapshotResponse.from(settlementService.markPaid(id)));
    }

    private static YearMonth parseYearMonthStrict(String raw) {
        try {
            return YearMonth.parse(raw, YEAR_MONTH_STRICT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_YEAR_MONTH,
                    "Invalid yearMonth format. Expected `YYYY-MM`. given=" + raw);
        }
    }
}
