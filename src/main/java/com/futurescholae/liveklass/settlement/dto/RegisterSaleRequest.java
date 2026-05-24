package com.futurescholae.liveklass.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record RegisterSaleRequest(
        String id,
        @NotBlank String courseId,
        @NotBlank String studentId,
        @Positive long amount,
        @NotNull OffsetDateTime paidAt
) {
}
