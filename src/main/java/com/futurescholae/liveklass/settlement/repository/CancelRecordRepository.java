package com.futurescholae.liveklass.settlement.repository;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CancelRecordRepository extends JpaRepository<CancelRecord, String> {

    @Query("""
            SELECT cr FROM CancelRecord cr
            JOIN SaleRecord s ON cr.saleId = s.id
            JOIN Course c ON s.courseId = c.id
            WHERE c.creatorId = :creatorId
              AND cr.cancelledAt >= :from
              AND cr.cancelledAt <  :toExclusive
            ORDER BY cr.cancelledAt ASC
            """)
    List<CancelRecord> findByCreatorAndCancelledAtRange(
            @Param("creatorId") String creatorId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    @Query("""
            SELECT cr FROM CancelRecord cr
            WHERE cr.cancelledAt >= :from
              AND cr.cancelledAt <  :toExclusive
            """)
    List<CancelRecord> findByCancelledAtRange(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    List<CancelRecord> findBySaleId(String saleId);

    @Query("""
            SELECT COALESCE(SUM(cr.refundAmount), 0) FROM CancelRecord cr
            WHERE cr.saleId = :saleId
            """)
    long sumRefundAmountBySaleId(@Param("saleId") String saleId);
}
