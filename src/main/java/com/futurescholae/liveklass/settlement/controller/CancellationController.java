package com.futurescholae.liveklass.settlement.controller;

import com.futurescholae.liveklass.settlement.dto.CancellationResponse;
import com.futurescholae.liveklass.settlement.dto.RegisterCancellationRequest;
import com.futurescholae.liveklass.settlement.service.CancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CancellationController {

    private final CancellationService cancellationService;

    @PostMapping("/cancellations")
    public ResponseEntity<CancellationResponse> register(@Valid @RequestBody RegisterCancellationRequest req) {
        var saved = cancellationService.register(
                req.id(), req.saleId(), req.refundAmount(),
                req.cancelledAt().toInstant());
        return ResponseEntity.status(HttpStatus.CREATED).body(CancellationResponse.from(saved));
    }
}
