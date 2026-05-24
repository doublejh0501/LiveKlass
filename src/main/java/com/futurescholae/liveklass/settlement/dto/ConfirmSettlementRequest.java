package com.futurescholae.liveklass.settlement.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSettlementRequest(
        @NotBlank String creatorId,
        @NotBlank String yearMonth
) {
}
