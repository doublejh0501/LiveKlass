package com.futurescholae.liveklass.settlement.dto;

import com.futurescholae.liveklass.settlement.domain.SaleRecord;

import java.time.Instant;

public record SaleResponse(
        String id,
        String courseId,
        String studentId,
        long amount,
        Instant paidAt
) {
    public static SaleResponse from(SaleRecord s) {
        return new SaleResponse(s.getId(), s.getCourseId(), s.getStudentId(), s.getAmount(), s.getPaidAt());
    }
}
