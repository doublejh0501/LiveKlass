package com.futurescholae.liveklass.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record RegisterCancellationRequest(
        String id,
        @NotBlank String saleId,
        @Positive long refundAmount,
        @NotNull OffsetDateTime cancelledAt
) {
}
