package com.futurescholae.liveklass.settlement.repository;

import com.futurescholae.liveklass.settlement.domain.SettlementSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementSnapshotRepository extends JpaRepository<SettlementSnapshot, Long> {

    Optional<SettlementSnapshot> findByCreatorIdAndPeriodYearMonth(String creatorId, String periodYearMonth);

    boolean existsByCreatorIdAndPeriodYearMonth(String creatorId, String periodYearMonth);
}
