package com.futurescholae.liveklass.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.Course;
import com.futurescholae.liveklass.settlement.domain.Creator;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import com.futurescholae.liveklass.settlement.dto.ConfirmSettlementRequest;
import com.futurescholae.liveklass.settlement.dto.RegisterCancellationRequest;
import com.futurescholae.liveklass.settlement.repository.CancelRecordRepository;
import com.futurescholae.liveklass.settlement.repository.CourseRepository;
import com.futurescholae.liveklass.settlement.repository.CreatorRepository;
import com.futurescholae.liveklass.settlement.repository.SaleRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SettlementIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private CreatorRepository creatorRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SaleRecordRepository saleRecordRepository;
    @Autowired private CancelRecordRepository cancelRecordRepository;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        cancelRecordRepository.deleteAll();
        saleRecordRepository.deleteAll();
        courseRepository.deleteAll();
        creatorRepository.deleteAll();
        loadFixture();
    }

    private void loadFixture() {
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
    }

    // ============================================================
    // 7.1 과제 제공 필수 시나리오
    // ============================================================

    @Test
    @DisplayName("[검증표] creator-1의 2025-03 월별 정산 — 총판매 260,000 / 환불 110,000 / 정산예정 120,000")
    void should_match_validation_table_for_creator1_march_2025() throws Exception {
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatorId", is("creator-1")))
                .andExpect(jsonPath("$.yearMonth", is("2025-03")))
                .andExpect(jsonPath("$.totalSalesAmount", is(260_000)))
                .andExpect(jsonPath("$.totalRefundAmount", is(110_000)))
                .andExpect(jsonPath("$.netSalesAmount", is(150_000)))
                .andExpect(jsonPath("$.commissionAmount", is(30_000)))
                .andExpect(jsonPath("$.payoutAmount", is(120_000)))
                .andExpect(jsonPath("$.saleCount", is(4)))
                .andExpect(jsonPath("$.cancelCount", is(2)));
    }

    @Test
    @DisplayName("[빈 월] creator-3의 2025-03 정산 → 200 + 모든 금액 0, count 0")
    void should_return_zero_for_creator_with_no_records_in_month() throws Exception {
        mockMvc.perform(get("/api/creators/creator-3/settlements")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(0)))
                .andExpect(jsonPath("$.totalRefundAmount", is(0)))
                .andExpect(jsonPath("$.netSalesAmount", is(0)))
                .andExpect(jsonPath("$.commissionAmount", is(0)))
                .andExpect(jsonPath("$.payoutAmount", is(0)))
                .andExpect(jsonPath("$.saleCount", is(0)))
                .andExpect(jsonPath("$.cancelCount", is(0)));
    }

    @Test
    @DisplayName("[월 경계] sale-5는 1월 판매에 귀속")
    void should_attribute_sale5_to_january_2025() throws Exception {
        mockMvc.perform(get("/api/creators/creator-2/settlements")
                        .param("yearMonth", "2025-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(60_000)))
                .andExpect(jsonPath("$.totalRefundAmount", is(0)))
                .andExpect(jsonPath("$.payoutAmount", is(48_000))); // 60,000 - 12,000
    }

    @Test
    @DisplayName("[음수 월] creator-2의 2025-02 — 판매 0 + 환불 60,000 → net -60,000 / commission 0 / payout -60,000")
    void should_return_negative_payout_for_creator2_february_2025() throws Exception {
        mockMvc.perform(get("/api/creators/creator-2/settlements")
                        .param("yearMonth", "2025-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(0)))
                .andExpect(jsonPath("$.totalRefundAmount", is(60_000)))
                .andExpect(jsonPath("$.netSalesAmount", is(-60_000)))
                .andExpect(jsonPath("$.commissionAmount", is(0)))
                .andExpect(jsonPath("$.payoutAmount", is(-60_000)))
                .andExpect(jsonPath("$.saleCount", is(0)))
                .andExpect(jsonPath("$.cancelCount", is(1)));
    }

    // ============================================================
    // 7.2 추가 권장 케이스
    // ============================================================

    @Test
    @DisplayName("[연월 형식] 2025-13 → 400 INVALID_YEAR_MONTH")
    void should_reject_invalid_year_month_13() throws Exception {
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_YEAR_MONTH")));
    }

    @Test
    @DisplayName("[연월 형식] 2025/03 → 400 INVALID_YEAR_MONTH")
    void should_reject_invalid_year_month_slash() throws Exception {
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025/03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_YEAR_MONTH")));
    }

    @Test
    @DisplayName("[연월 형식] 25-3 → 400 INVALID_YEAR_MONTH (자릿수 strict)")
    void should_reject_invalid_year_month_short() throws Exception {
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "25-3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_YEAR_MONTH")));
    }

    @Test
    @DisplayName("[누적 환불 초과] 80,000 결제 → 50,000 환불(OK) → 또 40,000 환불 시도 → 422")
    void should_reject_cumulative_refund_exceeding_payment() throws Exception {
        // 1차 환불 50,000 → OK
        RegisterCancellationRequest first = new RegisterCancellationRequest(
                null, "sale-1", 50_000L, OffsetDateTime.parse("2025-03-30T10:00:00+09:00"));
        mockMvc.perform(post("/api/cancellations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // 2차 환불 40,000 → 누적 90,000 > 원결제 50,000 이므로 422 (sale-1 원결제는 50,000)
        // 위 시나리오 보정: sale-1 원결제가 50,000이므로 한도는 50,000. 그래서 2차 시도가 더 명확히 초과.
        RegisterCancellationRequest second = new RegisterCancellationRequest(
                null, "sale-1", 1L, OffsetDateTime.parse("2025-03-30T11:00:00+09:00"));
        mockMvc.perform(post("/api/cancellations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("REFUND_EXCEEDS_PAYMENT")));
    }

    @Test
    @DisplayName("[부분 환불 초과] 단일 환불액이 원결제 초과 → 422")
    void should_reject_single_refund_exceeding_payment() throws Exception {
        RegisterCancellationRequest req = new RegisterCancellationRequest(
                null, "sale-1", 50_001L, OffsetDateTime.parse("2025-03-30T10:00:00+09:00"));
        mockMvc.perform(post("/api/cancellations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("REFUND_EXCEEDS_PAYMENT")));
    }

    @Test
    @DisplayName("[월 경계 정밀도] 2025-03-31T23:59:59+09:00은 3월에 포함, 2025-04-01T00:00:00+09:00은 4월에 포함")
    void should_apply_half_open_interval_at_month_boundary() throws Exception {
        // 3월 마지막 순간 직전 결제 (반드시 3월에 포함)
        saleRecordRepository.save(SaleRecord.builder()
                .id("boundary-march")
                .courseId("course-1")
                .studentId("boundary-s1")
                .amount(10_000)
                .paidAt(OffsetDateTime.parse("2025-03-31T23:59:59+09:00").toInstant())
                .build());
        // 4월 시작 정각 결제 (4월에 포함)
        saleRecordRepository.save(SaleRecord.builder()
                .id("boundary-april")
                .courseId("course-1")
                .studentId("boundary-s2")
                .amount(20_000)
                .paidAt(OffsetDateTime.parse("2025-04-01T00:00:00+09:00").toInstant())
                .build());

        // creator-1 / 3월: 기존 4건 + boundary-march = 270,000
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(270_000)))
                .andExpect(jsonPath("$.saleCount", is(5)));

        // creator-1 / 4월: boundary-april 만 = 20,000
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(20_000)))
                .andExpect(jsonPath("$.saleCount", is(1)));
    }

    @Test
    @DisplayName("[타임존 함정] UTC 시각이 KST 기준으로 다른 달로 넘어가는 경우 KST 월에 귀속")
    void should_attribute_by_kst_when_input_is_utc() throws Exception {
        // 2025-03-31T16:00:00Z = KST 2025-04-01T01:00 → 4월에 귀속되어야 함
        saleRecordRepository.save(SaleRecord.builder()
                .id("utc-boundary")
                .courseId("course-1")
                .studentId("utc-s1")
                .amount(15_000)
                .paidAt(OffsetDateTime.parse("2025-03-31T16:00:00Z").toInstant())
                .build());

        // 3월 정산엔 이 건이 포함되지 않아야 함 — 기본 4건만
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(260_000)))
                .andExpect(jsonPath("$.saleCount", is(4)));

        // 4월 정산에 포함
        mockMvc.perform(get("/api/creators/creator-1/settlements")
                        .param("yearMonth", "2025-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount", is(15_000)))
                .andExpect(jsonPath("$.saleCount", is(1)));
    }

    // ============================================================
    // 운영자 집계 API
    // ============================================================

    @Test
    @DisplayName("[운영자 집계] 2025-03-01 ~ 2025-03-31 기간 — 크리에이터별 + 전체 합계")
    void should_aggregate_admin_settlements_for_march_2025() throws Exception {
        // creator-1: payout 120,000 / creator-2: sale-6(60,000)만 → payout 48,000 / creator-3: 0
        mockMvc.perform(get("/api/admin/settlements")
                        .param("from", "2025-03-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", is("2025-03-01")))
                .andExpect(jsonPath("$.to", is("2025-03-31")))
                .andExpect(jsonPath("$.creators[?(@.creatorId == 'creator-1')].payoutAmount",
                        org.hamcrest.Matchers.hasItem(120_000)))
                .andExpect(jsonPath("$.creators[?(@.creatorId == 'creator-2')].payoutAmount",
                        org.hamcrest.Matchers.hasItem(48_000)))
                .andExpect(jsonPath("$.totalPayoutAmount", is(168_000)));
    }

    // ============================================================
    // 가산점 — 중복 정산 방지
    // ============================================================

    @Test
    @DisplayName("[가산점] 동일 (creator, yearMonth) 재확정 시 409 DUPLICATE_SETTLEMENT")
    void should_reject_duplicate_settlement_confirmation() throws Exception {
        ConfirmSettlementRequest req = new ConfirmSettlementRequest("creator-1", "2025-03");
        mockMvc.perform(post("/api/admin/settlements/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.settledAmount", is(120_000)));

        // 동일 기간 재확정 → 409
        mockMvc.perform(post("/api/admin/settlements/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_SETTLEMENT")));
    }

    // ============================================================
    // 판매 등록 / 조회
    // ============================================================

    @Test
    @DisplayName("[판매 조회] 크리에이터별 기간 조회로 판매 내역 목록 반환")
    void should_list_sales_by_creator_and_date_range() throws Exception {
        mockMvc.perform(get("/api/creators/creator-1/sales")
                        .param("from", "2025-03-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(4)));
    }

    private static SaleRecord sale(String id, String courseId, String studentId, long amount, String iso) {
        return SaleRecord.builder()
                .id(id).courseId(courseId).studentId(studentId).amount(amount)
                .paidAt(OffsetDateTime.parse(iso).toInstant()).build();
    }

    private static CancelRecord cancel(String id, String saleId, long refundAmount, String iso) {
        return CancelRecord.builder()
                .id(id).saleId(saleId).refundAmount(refundAmount)
                .cancelledAt(OffsetDateTime.parse(iso).toInstant()).build();
    }
}
