package com.futurescholae.liveklass.settlement.service;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.Course;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import com.futurescholae.liveklass.settlement.exception.BusinessException;
import com.futurescholae.liveklass.settlement.exception.ErrorCode;
import com.futurescholae.liveklass.settlement.repository.CancelRecordRepository;
import com.futurescholae.liveklass.settlement.repository.CourseRepository;
import com.futurescholae.liveklass.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRecordRepository saleRecordRepository;
    private final CourseRepository courseRepository;
    private final CancelRecordRepository cancelRecordRepository;

    @Transactional
    public SaleRecord register(String saleId, String courseId, String studentId,
                               long amount, java.time.Instant paidAt) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COURSE_NOT_FOUND,
                        "Course not found: " + courseId));

        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Sale amount must be positive");
        }

        String id = (saleId != null && !saleId.isBlank()) ? saleId : UUID.randomUUID().toString();
        SaleRecord sale = SaleRecord.builder()
                .id(id)
                .courseId(course.getId())
                .studentId(studentId)
                .amount(amount)
                .paidAt(paidAt)
                .build();
        return saleRecordRepository.save(sale);
    }

    @Transactional(readOnly = true)
    public List<SaleRecord> findByCreatorAndPeriod(String creatorId, LocalDate from, LocalDate to) {
        MonthRange range = MonthRange.ofDateRangeKst(from, to);
        return saleRecordRepository.findByCreatorAndPaidAtRange(creatorId,
                range.fromInclusive(), range.toExclusive());
    }

    @Transactional(readOnly = true)
    public List<CancelRecord> findCancelsForSales(List<String> saleIds) {
        return saleIds.stream()
                .flatMap(id -> cancelRecordRepository.findBySaleId(id).stream())
                .toList();
    }
}
