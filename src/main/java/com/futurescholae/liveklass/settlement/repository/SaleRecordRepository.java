package com.futurescholae.liveklass.settlement.repository;

import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SaleRecordRepository extends JpaRepository<SaleRecord, String> {

    @Query("""
            SELECT s FROM SaleRecord s
            JOIN Course c ON s.courseId = c.id
            WHERE c.creatorId = :creatorId
              AND s.paidAt >= :from
              AND s.paidAt <  :toExclusive
            ORDER BY s.paidAt ASC
            """)
    List<SaleRecord> findByCreatorAndPaidAtRange(
            @Param("creatorId") String creatorId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );

    @Query("""
            SELECT s FROM SaleRecord s
            WHERE s.paidAt >= :from
              AND s.paidAt <  :toExclusive
            """)
    List<SaleRecord> findByPaidAtRange(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive
    );
}
