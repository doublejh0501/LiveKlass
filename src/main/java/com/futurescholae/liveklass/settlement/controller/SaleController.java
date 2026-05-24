package com.futurescholae.liveklass.settlement.controller;

import com.futurescholae.liveklass.settlement.dto.RegisterSaleRequest;
import com.futurescholae.liveklass.settlement.dto.SaleResponse;
import com.futurescholae.liveklass.settlement.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping("/sales")
    public ResponseEntity<SaleResponse> register(@Valid @RequestBody RegisterSaleRequest req) {
        var saved = saleService.register(
                req.id(), req.courseId(), req.studentId(),
                req.amount(), req.paidAt().toInstant());
        return ResponseEntity.status(HttpStatus.CREATED).body(SaleResponse.from(saved));
    }

    @GetMapping("/creators/{creatorId}/sales")
    public List<SaleResponse> listByCreator(
            @PathVariable String creatorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return saleService.findByCreatorAndPeriod(creatorId, from, to).stream()
                .map(SaleResponse::from)
                .toList();
    }
}
