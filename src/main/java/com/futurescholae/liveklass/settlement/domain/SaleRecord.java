package com.futurescholae.liveklass.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "sale_records", indexes = {
        @Index(name = "idx_sale_course", columnList = "course_id"),
        @Index(name = "idx_sale_paid_at", columnList = "paid_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SaleRecord {

    @Id
    private String id;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;
}
