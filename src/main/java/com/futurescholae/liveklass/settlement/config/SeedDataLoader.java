package com.futurescholae.liveklass.settlement.config;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.Course;
import com.futurescholae.liveklass.settlement.domain.Creator;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import com.futurescholae.liveklass.settlement.repository.CancelRecordRepository;
import com.futurescholae.liveklass.settlement.repository.CourseRepository;
import com.futurescholae.liveklass.settlement.repository.CreatorRepository;
import com.futurescholae.liveklass.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 과제 §6.1 + §6.2 시드 데이터 주입.
 * 환불은 과제 JSON엔 없지만, 검증표 기대값과 맞추기 위해 작업지시서 §6.2의 cancel-1/2/3을 추가 주입.
 */
@Component
@ConditionalOnProperty(name = "settlement.seed-data.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader implements CommandLineRunner {

    private final CreatorRepository creatorRepository;
    private final CourseRepository courseRepository;
    private final SaleRecordRepository saleRecordRepository;
    private final CancelRecordRepository cancelRecordRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (creatorRepository.count() > 0) {
            log.info("Seed data already loaded, skipping.");
            return;
        }
        log.info("Loading seed data for BE-B settlement assignment...");

        creatorRepository.saveAll(List.of(
                Creator.builder().id("creator-1").name("김강사").build(),
                Creator.builder().id("creator-2").name("이강사").build(),
                Creator.builder().id("creator-3").name("박강사").build()
        ));

        courseRepository.saveAll(List.of(
                Course.builder().id("course-1").creatorId("creator-1").title("Spring Boot 입문").build(),
                Course.builder().id("course-2").creatorId("creator-1").title("JPA 실전").build(),
                Course.builder().id("course-3").creatorId("creator-2").title("Kotlin 기초").build(),
                Course.builder().id("course-4").creatorId("creator-3").title("MSA 설계").build()
        ));

        saleRecordRepository.saveAll(List.of(
                sale("sale-1", "course-1", "student-1", 50_000, "2025-03-05T10:00:00+09:00"),
                sale("sale-2", "course-1", "student-2", 50_000, "2025-03-15T14:30:00+09:00"),
                sale("sale-3", "course-2", "student-3", 80_000, "2025-03-20T09:00:00+09:00"),
                sale("sale-4", "course-2", "student-4", 80_000, "2025-03-22T11:00:00+09:00"),
                sale("sale-5", "course-3", "student-5", 60_000, "2025-01-31T23:30:00+09:00"),
                sale("sale-6", "course-3", "student-6", 60_000, "2025-03-10T16:00:00+09:00"),
                sale("sale-7", "course-4", "student-7", 120_000, "2025-02-14T10:00:00+09:00")
        ));

        cancelRecordRepository.saveAll(List.of(
                cancel("cancel-1", "sale-3", 80_000, "2025-03-25T10:00:00+09:00"),
                cancel("cancel-2", "sale-4", 30_000, "2025-03-26T10:00:00+09:00"),
                cancel("cancel-3", "sale-5", 60_000, "2025-02-02T09:00:00+09:00")
        ));

        log.info("Seed data loaded: creators={}, courses={}, sales={}, cancels={}",
                creatorRepository.count(), courseRepository.count(),
                saleRecordRepository.count(), cancelRecordRepository.count());
    }

    private static SaleRecord sale(String id, String courseId, String studentId, long amount, String isoOffsetDateTime) {
        return SaleRecord.builder()
                .id(id)
                .courseId(courseId)
                .studentId(studentId)
                .amount(amount)
                .paidAt(OffsetDateTime.parse(isoOffsetDateTime).toInstant())
                .build();
    }

    private static CancelRecord cancel(String id, String saleId, long refundAmount, String isoOffsetDateTime) {
        return CancelRecord.builder()
                .id(id)
                .saleId(saleId)
                .refundAmount(refundAmount)
                .cancelledAt(OffsetDateTime.parse(isoOffsetDateTime).toInstant())
                .build();
    }
}
