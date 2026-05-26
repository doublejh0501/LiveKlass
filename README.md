# LiveKlass 크리에이터 정산 API (BE-B)

(주)퓨쳐스콜레 **프로덕트 엔지니어 채용 과제 — BE-B**. 라이브클래스(LiveKlass) 플랫폼의 크리에이터 정산 도메인을 모델링하고, 월별·기간별 정산 금액을 1원 단위로 정확히 산출하는 백엔드 API.

> 라이브클래스의 핵심 비즈니스 지표는 **"크리에이터 매출"**(2024년 236억 원)이다. 본 과제는 회사의 mission-critical 도메인 그 자체이므로, **정산 금액이 1원이라도 틀리면 안 된다**는 관점으로 모든 정책을 글로 명시하고 테스트로 증명했다.

---

## 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 과제 | BE-B. 크리에이터 정산 API |
| 핵심 시나리오 | 판매 등록 / 부분 환불 / 월별 정산 / 운영자 집계 / 월 경계·타임존·음수 월 처리 |
| 도메인 | 크리에이터·강의·판매·취소·정산 스냅샷 |
| 평가 포커스 | 비즈니스 규칙 정확성 + 정책 결정의 명시성 + 테스트 커버리지 |

## 기술 스택

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어 | **Java 21** | LTS, record/스트림/패턴 매칭 활용 |
| 프레임워크 | **Spring Boot 3.4.1** | 과제 필수 |
| ORM | **Spring Data JPA** | 도메인(판매/취소/정산) 표현이 명확. 집계 쿼리는 JPQL로 충분 |
| DB | **H2 (in-memory)** | `git clone → ./gradlew bootRun → 테스트`가 한 번에 검증 가능 |
| 빌드 | **Gradle (wrapper)** | 환경 의존 0 |
| 테스트 | **JUnit 5 + Spring Boot Test + MockMvc + AssertJ** | BE 과제 필수, 검증표 수치를 단위·통합 양쪽에서 증명 |
| 보일러플레이트 | **Lombok** | 엔티티 / DTO 표현을 짧게 |

## 실행 방법

```bash
# JDK 21 필요 (확인: java -version)
./gradlew bootRun
# → http://localhost:8080 에서 기동, 시드 데이터 자동 주입
#   H2 콘솔: http://localhost:8080/h2-console   (JDBC URL: jdbc:h2:mem:settlement)
```

```bash
# 테스트 실행
./gradlew test
# → 단위 7개(SettlementCalculatorTest) + 통합 14개(SettlementIntegrationTest) = 21개 통과
```

시드 데이터는 `application.yml`의 `settlement.seed-data.enabled=true` 일 때 `SeedDataLoader`(`CommandLineRunner`)가 과제 §6.1 데이터 + 작업지시서 §6.2(검증표에서 역산한 취소 데이터)를 주입한다.

### Swagger UI / OpenAPI

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html (클릭 한 번에 API 호출·검증 가능)
- **OpenAPI 문서(JSON)**: http://localhost:8080/v3/api-docs

`springdoc-openapi`로 자동 생성. 별도 어노테이션(`@Operation` 등)은 의도적으로 생략 — 자동 생성만으로 7개 엔드포인트와 DTO 스키마가 등록된다(과설계 방지).

---

## 요구사항 해석 및 가정

| 요구사항 원문 | 본 구현의 해석 |
|---|---|
| "정산 기간 기준: 결제 완료 일시 기준(취소는 취소 일시 기준)" | 환불을 **원판매 월로 소급하지 않음**. 1월 판매 / 2월 취소 → 1월 정산엔 판매가, 2월 정산엔 환불이 잡힌다. |
| "월 경계: 1일 00:00:00 ~ 말일 23:59:59 (KST 기준)" | DB 쿼리는 `paidAt >= 월초 KST AND paidAt < 다음달초 KST`의 **반열린 구간**으로 처리(밀리초 누락 방지). 자세한 근거는 아래 §설계 결정 1 참조. |
| "수수료율은 일단 고정값 20%로 구현" | `application.yml`의 `settlement.commission-rate`로 설정값화. 시점(date)을 받는 `CommissionRateProvider.rateAt(date)` 추상화로 향후 "수수료율 이력 관리" 가산점 기능 추가 시 메서드만 교체. |
| "동일 기간 중복 정산 방지" | `SettlementSnapshot` 엔티티에 `UNIQUE(creator_id, period_year_month)` 제약 + 서비스 레이어에서 사전 체크 + `DataIntegrityViolationException` 잡아 409 응답. |
| "환불 금액 ≠ 원결제 금액" (부분 환불) | 동일 판매에 다수 취소 허용, 단 **누적 환불액이 원결제를 초과하면 거부(422)**. |

명세에 명시되지 않은 지점(특히 **음수 월의 수수료**)은 임의로 채우지 않고 정책을 명시적으로 결정하고 근거를 남기는 방식으로 처리했다. 다음 절 참조.

---

## 설계 결정과 이유

### 1. 시각 처리: UTC `Instant` 저장 + KST 계산

- **저장**: 모든 시각 컬럼은 `Instant`(UTC). `LocalDateTime`은 오프셋 정보가 날아가므로 금지.
- **월 경계 계산**: `MonthRange.ofYearMonthKst(yearMonth)`에서 `Asia/Seoul` 기준으로 [월초, 다음달초)의 UTC `Instant` 쌍을 만들고, 쿼리는 그 구간으로 `<`(반열린) 비교.
- **왜 "말일 23:59:59 ≤" 가 아니라 "< 다음달초"인가**: `23:59:59.000~999` 밀리초 구간이 누락될 위험이 있다. 다음 달 시작 시각 미만 비교는 정밀도와 무관하게 안전하다.
- **검증**: `2025-03-31T16:00:00Z`(UTC) = KST `2025-04-01T01:00`인 입력이 **4월에 귀속**되는지 통합 테스트(`should_attribute_by_kst_when_input_is_utc`)로 증명.

### 2. 음수 월 수수료: 클램프(비환불) 정책

환불이 그 달 판매보다 큰 달은 순판매가 **음수**가 된다. 이때 수수료를 어떻게 산정할지가 평가 변별 지점이다.

본 구현은 다음 정책을 채택했다:

```
netSales   = totalSales - totalRefund        // 음수 그대로 노출
commission = floor( max(netSales, 0) × rate ) // 음수 순판매에는 수수료 미부과
payout     = netSales - commission            // 환불만 있는 달 → payout = netSales (음수)
```

**의미: 수수료 비환불(non-refundable) 정책.** 판매 월에 부과한 20% 수수료는 환불이 발생해도 환원하지 않는다. 그래서 환불 월의 수수료는 0, payout은 음수(크리에이터에게서 차감).

**왜 "음수 순판매에도 비례 수수료 환원(net × 0.8)"이 아닌가**: 그 모델은 "왜 플랫폼이 수수료를 돌려주냐"라는 역질문에 걸린다. 실제 마켓플레이스 정산 대부분은 비환불 정책이며, 방어가 더 명확하다.

**왜 "음수를 0으로 클램프"가 아닌가**: 환불액이 증발해서 누적 정산 검산이 깨진다. 음수 = "이번 달 차감(클로백) 금액"으로 그대로 노출하는 것이 회계적으로 정합.

### 3. 금액 타입: `long` (BigDecimal 미사용)

- KRW는 **소수점이 없는 통화**(원 단위)이므로 부동소수 오차 우려가 없다.
- `long` 정수 저장으로 오차를 원천 차단. `BigDecimal`까지 갈 필요 없음.
- 수수료 계산만 `BigDecimal × BigDecimal` 후 `setScale(0, FLOOR).longValueExact()`로 원 단위 절사(floor) 후 다시 `long`. 합계 검산이 깨지지 않도록 **payout 은 `netSales − commission`으로 도출**(독립 계산 금지).

### 4. 정규화 유지(`creatorId`는 `Course`로 join)

- `SaleRecord`에 `creatorId`를 비정규화하지 않고 `Course`를 통해 조회.
- 과제 규모에선 join으로 충분하며, 비정규화는 중복 컬럼 정합성 관리 부담만 늘린다.
- 성능 이슈가 실제로 측정될 때 도입할 일종의 **YAGNI**.

### 5. 환불 무결성: 누적 환불 초과 방지(422)

```text
register(cancel) {
    sale = saleRepo.findById(saleId)
    existing = sum(refunds where saleId)
    if existing + new > sale.amount → 422 REFUND_EXCEEDS_PAYMENT
}
```

- 단일 트랜잭션 내 합산 검증. 분산 환경 동시성까지 빡세게 가지는 않지만, 실서비스에서 가장 흔한 정산 사고를 방어.

### 6. 계산기 / 서비스 분리 (구현 품질)

- `SettlementCalculator` = **순수 도메인 객체**. Spring / Repository / JPA 의존 0. POJO 단위 테스트로 모든 정책(음수/절사/합계 검산/수수료율 주입 가능)을 증명.
- `SettlementService` = 데이터 조회. 기간 내 판매·취소를 DB에서 모아 Calculator에 위임.

이 구조 덕분에 **DB 없이도 정산 규칙 전부를 검증**할 수 있다(`SettlementCalculatorTest` 7개).

### 7. 에러 응답 통일 (`@RestControllerAdvice`)

```json
{ "code": "REFUND_EXCEEDS_PAYMENT", "message": "Cumulative refund 90000 exceeds original payment 80000 ..." }
```

| 코드 | HTTP | 발생 시점 |
|---|---|---|
| `INVALID_YEAR_MONTH` | 400 | `2025-13`, `2025/03`, `25-3` 등 strict 파싱 실패 |
| `INVALID_REQUEST` | 400 | DTO `@Valid` 실패, 파라미터 누락, 형식 변환 실패 |
| `INVALID_DATE_RANGE` | 400 | 운영자 집계에서 `to < from` |
| `SALE_NOT_FOUND` | 404 | 취소 등록 시 원본 판매 없음 |
| `COURSE_NOT_FOUND` | 404 | 판매 등록 시 강의 없음 |
| `SETTLEMENT_NOT_FOUND` | 404 | 지급 처리 대상 스냅샷 없음 |
| `DUPLICATE_SETTLEMENT` | 409 | 동일 (크리에이터, 연월) 재확정 |
| `INVALID_SETTLEMENT_STATE` | 409 | CONFIRMED 가 아닌 스냅샷을 PAID로 전이 시도 |
| `REFUND_EXCEEDS_PAYMENT` | 422 | 누적/단일 환불액이 원결제 초과 |

**형식 오류(400) vs 규칙 위반(422)의 구분**을 명확히 했다.

### 8. 빈 월 응답 정책

판매·취소 없는 월 조회 → **200 + 모든 금액 0**. 404 가 아니라 "데이터 없음 = 0원 정상 응답"으로 통일 (정책 일관성).

### 9. 수수료율 적용 정책 — "정산월 기준 단일 율"

환불은 §설계 결정 1에 따라 취소월에 귀속된다. 그래서 "1월 판매 / 2월 취소"의 환불은 2월 정산에 잡힌다. **그 환불에 어떤 율을 적용해야 하는가?** 이건 명세가 침묵하는 underspecified 지점이다.

본 구현은 다음 정책을 채택했다:

- **월별 정산(`calculateMonthly`)**: 정산이 발생한 월의 율(= 정산 월 1일 시점의 율)을 **단일 율로 일관 적용**. 한 정산 응답 안의 모든 라인이 같은 율로 계산된다.
- **운영자 기간 집계(`aggregate`)**: 기간 시작일(`from`) 시점의 율을 **단일 율로 적용**. 율 변경 시점을 걸치는 기간은 본 구현 범위 밖이며, 이력 테이블 도입 시 "기간 분할 + 분할별 율 매칭" 로직으로 교체할 수 있도록 `CommissionRateProvider.rateAt(date)` 시그니처는 이미 시점 인자를 받는 형태로 추상화돼 있다.

이 정책은 코드 주석(`SettlementService.calculateMonthly`, `SettlementService.aggregate`)에도 명시돼 있다.

### 10. 운영자 집계의 성능 — 의도적 단순 구현

`aggregate`는 기간 내 전체 판매·취소·코스를 메모리로 로드한 뒤 크리에이터별로 그룹핑한다. 과제 규모에선 단순·충분하지만, 운영 규모에선:
- `GROUP BY creator_id` 로 DB 집계를 내리거나
- 페이지네이션 / 스트리밍 처리

가 필요하다. 작업지시서 §1.5(과설계 방지)에 따라 **현재는 단순 구현을 유지**하고, 확장은 실제 데이터 규모가 측정되는 시점에 도입한다.

---

## API 목록 및 예시

### `POST /api/sales` — 판매 등록
```bash
curl -X POST http://localhost:8080/api/sales \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "sale-new", "courseId": "course-1",
    "studentId": "student-99", "amount": 50000,
    "paidAt": "2025-03-05T10:00:00+09:00"
  }'
```
응답: `201 Created` + 등록된 판매 객체.

### `POST /api/cancellations` — 취소(환불) 등록
```bash
curl -X POST http://localhost:8080/api/cancellations \
  -H 'Content-Type: application/json' \
  -d '{
    "saleId": "sale-4", "refundAmount": 30000,
    "cancelledAt": "2025-03-26T10:00:00+09:00"
  }'
```
응답: `201 Created`. 누적 환불액 > 원결제 → `422 REFUND_EXCEEDS_PAYMENT`.

### `GET /api/creators/{creatorId}/sales` — 판매 내역 목록 (크리에이터별, 기간 필터)
```bash
curl 'http://localhost:8080/api/creators/creator-1/sales?from=2025-03-01&to=2025-03-31'
```

### `GET /api/creators/{creatorId}/settlements?yearMonth=2025-03` — 월별 정산 조회
```bash
curl 'http://localhost:8080/api/creators/creator-1/settlements?yearMonth=2025-03'
```
```json
{
  "creatorId" : "creator-1",
  "yearMonth" : "2025-03",
  "totalSalesAmount" : 260000,
  "totalRefundAmount" : 110000,
  "netSalesAmount" : 150000,
  "commissionRate" : 0.20,
  "commissionAmount" : 30000,
  "payoutAmount" : 120000,
  "saleCount" : 4,
  "cancelCount" : 2
}
```

**과제 명세 응답 필드 매핑** (한글 ↔ JSON 키):

| 과제 명세 (한글) | 응답 필드 |
|---|---|
| 해당 월 총 판매 금액 | `totalSalesAmount` |
| 취소/환불 금액 | `totalRefundAmount` |
| 순 판매 금액 (= 총 판매 − 환불) | `netSalesAmount` |
| 플랫폼 수수료 (순 판매의 20%) | `commissionAmount` (+ `commissionRate`) |
| 정산 예정 금액 (= 순 판매 − 수수료) | `payoutAmount` |
| 판매 건수 / 취소 건수 | `saleCount` / `cancelCount` |

### `GET /api/admin/settlements?from=&to=` — 운영자 기간 집계
```bash
curl 'http://localhost:8080/api/admin/settlements?from=2025-03-01&to=2025-03-31'
```
```json
{
  "from": "2025-03-01", "to": "2025-03-31",
  "creators": [
    { "creatorId": "creator-1", "payoutAmount": 120000, ... },
    { "creatorId": "creator-2", "payoutAmount": 48000,  ... }
  ],
  "totalPayoutAmount": 168000
}
```

### `POST /api/admin/settlements/confirm` — (가산점) 정산 확정
```bash
curl -X POST http://localhost:8080/api/admin/settlements/confirm \
  -H 'Content-Type: application/json' \
  -d '{ "creatorId": "creator-1", "yearMonth": "2025-03" }'
```
`200 OK` + 스냅샷. 동일 (creatorId, yearMonth) 재호출 → `409 DUPLICATE_SETTLEMENT`.

### `POST /api/admin/settlements/{id}/pay` — (가산점) 지급 처리
`CONFIRMED → PAID` 상태 전이. 그 외 상태에서 호출 → `409 INVALID_SETTLEMENT_STATE`.

---

## 데이터 모델 설명

### ERD (관계도)

```
Creator (id PK, name)
   ↑ creator_id (FK 정규화 유지)
Course (id PK, creator_id, title)
   ↑ course_id
SaleRecord (id PK, course_id, student_id, amount, paid_at)
   ↑ sale_id
CancelRecord (id PK, sale_id, refund_amount, cancelled_at)

SettlementSnapshot (id PK, creator_id, period_year_month, status,
                    settled_amount, confirmed_at, paid_at)
   UNIQUE(creator_id, period_year_month)   ← 동일 기간 중복 정산 방지
```

### 테이블 컬럼 상세

**`creators`** — 크리에이터(강사) 마스터
| 컬럼 | 타입 | NULL | 제약 / 인덱스 | 설명 |
|---|---|---|---|---|
| `id` | VARCHAR | NOT NULL | **PK** | 크리에이터 ID (예: `creator-1`) |
| `name` | VARCHAR | NULL | | 크리에이터 이름 |

**`courses`** — 강의 마스터
| 컬럼 | 타입 | NULL | 제약 / 인덱스 | 설명 |
|---|---|---|---|---|
| `id` | VARCHAR | NOT NULL | **PK** | 강의 ID (예: `course-1`) |
| `creator_id` | VARCHAR | NOT NULL | `idx_course_creator` | 소유 크리에이터 (논리 FK → `creators.id`) |
| `title` | VARCHAR | NULL | | 강의 제목 |

**`sale_records`** — 판매 내역
| 컬럼 | 타입 | NULL | 제약 / 인덱스 | 설명 |
|---|---|---|---|---|
| `id` | VARCHAR | NOT NULL | **PK** | 판매 ID |
| `course_id` | VARCHAR | NOT NULL | `idx_sale_course` | 강의 ID (논리 FK → `courses.id`) |
| `student_id` | VARCHAR | NOT NULL | | 수강생 ID (단순 데이터 필드, 호출 주체 아님) |
| `amount` | BIGINT (`long`) | NOT NULL | `> 0` (DTO `@Positive`) | 결제 금액 (KRW 원 단위) |
| `paid_at` | TIMESTAMP (`Instant` UTC) | NOT NULL | `idx_sale_paid_at` | 결제 완료 일시 (UTC 저장 / KST 변환 계산) |

**`cancel_records`** — 취소(환불) 내역
| 컬럼 | 타입 | NULL | 제약 / 인덱스 | 설명 |
|---|---|---|---|---|
| `id` | VARCHAR | NOT NULL | **PK** | 취소 ID |
| `sale_id` | VARCHAR | NOT NULL | `idx_cancel_sale` | 원본 판매 ID (논리 FK → `sale_records.id`) |
| `refund_amount` | BIGINT (`long`) | NOT NULL | `> 0` + 누적 ≤ 원결제(서비스 검증) | 환불 금액 |
| `cancelled_at` | TIMESTAMP (`Instant` UTC) | NOT NULL | `idx_cancel_cancelled_at` | 취소 일시 |

**`settlement_snapshots`** — 정산 확정 스냅샷 (가산점)
| 컬럼 | 타입 | NULL | 제약 / 인덱스 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | NOT NULL | **PK** (IDENTITY) | 스냅샷 ID |
| `creator_id` | VARCHAR | NOT NULL | **UNIQUE**(creator_id, period_year_month) | 정산 대상 크리에이터 |
| `period_year_month` | VARCHAR(7) | NOT NULL | 〃 | 정산 월 (예: `2025-03`) |
| `status` | VARCHAR(16) | NOT NULL | ENUM | `PENDING` / `CONFIRMED` / `PAID` |
| `settled_amount` | BIGINT | NOT NULL | | 확정 시점의 정산 예정 금액 |
| `confirmed_at` | TIMESTAMP | NULL | | 확정 일시 |
| `paid_at` | TIMESTAMP | NULL | | 지급 일시 |

### 설계 메모

- **정규화 유지**: `creator_id` 는 `sale_records`에 비정규화하지 않고 `courses`를 join으로 도출(§설계 결정 4). 과제 규모에서 join 비용은 무시 가능하며, 비정규화는 중복 컬럼 정합성 부담만 늘림.
- **시각**: 모두 `Instant` (UTC) 저장 → 월 경계 계산만 `Asia/Seoul` 변환. `LocalDateTime` 금지(오프셋 손실).
- **금액**: 모두 `long` (KRW, 원 단위). `BigDecimal` 미사용(소수점 통화가 아님).
- **FK**: JPA 엔티티 관계는 매핑하지 않고 ID로만 연결(**논리 FK**). 과제 규모와 H2 in-memory 환경에서 join 쿼리는 JPQL로 명시. (실서비스라면 `@ManyToOne` + lazy loading + FK 제약 추가 검토.)
- **인덱스**: 5개 — 모두 조회 패턴(기간 필터 + creator/course/sale join)에서 실제로 사용됨.

---

## 테스트 실행 방법

```bash
./gradlew test
# build/reports/tests/test/index.html 에서 HTML 리포트 확인 가능
```

### 단위 테스트 (`SettlementCalculatorTest` × 7)
DB 의존 없이 정산 정책 전체를 증명.

| 케이스 | 검증 포인트 |
|---|---|
| `should_match_assignment_validation_table_for_creator1_march` | 검증표 수치(260,000/110,000/150,000/30,000/120,000/4/2) 그대로 |
| `should_return_zero_for_month_with_no_records` | 빈 입력 → 모든 금액 0, count 0 |
| `should_calculate_negative_payout_when_only_refund_exists` | 환불만 있는 달 → net 음수 / commission 0 / payout = net |
| `should_reflect_partial_refund_in_net_sales` | 부분 환불 정확 반영 |
| `should_sum_multiple_cancels_in_same_month` | 동일 월 다수 취소 합산 |
| `should_floor_commission_and_preserve_payout_invariant` | 999 × 0.2 = 199.8 → floor 199, payout 합계 검산 |
| `should_accept_pluggable_commission_rate` | 수수료율 주입 가능(변경 가능성 설계 반영) |

### 통합 테스트 (`SettlementIntegrationTest` × 21)
시드 픽스처를 주입 후 MockMvc로 실제 API 요청/응답 JSON 검증.

- 검증표 4종 (creator-1 3월 / 빈 월 / 월 경계 1월 sale-5 / 음수 월 creator-2 2월)
- 연월 형식 오류 3종 (`2025-13`, `2025/03`, `25-3` → 400)
- 환불 무결성 2종 (단일 초과 / 누적 초과 → 422)
- 월 경계 정밀도 (`23:59:59 KST` 포함, 다음달 `00:00:00 KST` 제외)
- 타임존 함정 (`2025-03-31T16:00:00Z` → 4월 귀속)
- 운영자 집계 (전체 합계 168,000)
- 가산점: 중복 정산 방지 (409)
- 판매 조회 (크리에이터별 기간 필터)
- 미래 월 조회 (`2099-12` → 200 + 0)
- 운영자 집계 `to < from` (400 INVALID_DATE_RANGE)
- 운영자 집계 잘못된 날짜 형식 (400 INVALID_REQUEST)
- 존재하지 않는 `saleId` 로 취소 (404 SALE_NOT_FOUND)
- 존재하지 않는 `courseId` 로 판매 (404 COURSE_NOT_FOUND)
- amount/refundAmount ≤ 0 (400 INVALID_REQUEST, `@Positive`)

---

## 추가한 엣지케이스 — 위험 → 대처 증명

과제 요구서의 "추가 데이터 가이드"는 본질적으로 **"오류·엣지 시나리오에 대한 데이터를 직접 셋업하고, 안전하게 대처됨을 증명하라"**는 요구다. 시드 데이터를 늘리는 게 아니라 **각 시나리오의 데이터를 테스트 픽스처로 셋업 → 응답을 assert** 하는 방식으로 12개 케이스를 박았다.

### 도메인 규칙 검증 (5)

| # | 시나리오 데이터 | 안 막으면 발생하는 위험 | 실제 대처 (검증된 응답) |
|---|---|---|---|
| 1 | 환불만 있는 월 (sale 0건 + cancel 60,000) | 음수 net 에 비례 수수료 환원 → 플랫폼 손실 / 음수→0 클램프 시 환불액 증발 | `net=-60,000 / commission=0 / payout=-60,000` (비환불 정책) |
| 2 | `2025-03-31T23:59:59+09:00` + `2025-04-01T00:00:00+09:00` 결제 | 닫힘 비교 시 3월 마지막 1초 누락 → 정산 1원 오차 | 반열린 구간으로 3월에 23:59:59 포함, 4월에 00:00:00 포함 |
| 3 | `2025-03-31T16:00:00Z`(UTC) 결제 | 서버 타임존 따라 다른 달로 새어나감 | KST 변환 후 4월(KST 4/1 01:00)에 정확 귀속 |
| 4 | 50,000 결제 → 50,000 환불(OK) → 추가 1원 환불 시도 | 음수 잔액 / 회계 사고 | 422 `REFUND_EXCEEDS_PAYMENT` (누적 51,000 > 50,000) |
| 5 | 원결제 50,000 → 단일 환불 50,001 시도 | 음수 잔액 | 422 `REFUND_EXCEEDS_PAYMENT` |

### 입력 검증 / 형식 오류 (7)

| # | 시나리오 데이터 | 안 막으면 발생하는 위험 | 실제 대처 (검증된 응답) |
|---|---|---|---|
| 6 | `2025-13` / `2025/03` / `25-3` | 파싱 에러 500 새어나감 / 무효 월이 0월·13월로 흘러감 | 400 `INVALID_YEAR_MONTH` (`DateTimeFormatter` strict) |
| 7 | `2099-12` 미래 월 조회 | 404 vs 0원 불일관 / 다른 빈 월과 다른 응답 | 200 + 모든 금액 0 (빈 월 정책 일관) |
| 8 | 운영자 집계 `from=2025-03-31&to=2025-03-01` | 빈 결과 / SQL 무한 루프 / 운영자 혼란 | 400 `INVALID_DATE_RANGE` |
| 9 | 운영자 집계 `from=2025/03/01` | 파싱 에러 500 새어나감 | 400 `INVALID_REQUEST` |
| 10 | 존재하지 않는 `saleId` 로 취소 | NPE / FK 위반 / 환불 합산 깨짐 | 404 `SALE_NOT_FOUND` |
| 11 | 존재하지 않는 `courseId` 로 판매 | 고아 레코드 / 정산 조회 시 join 깨짐 | 404 `COURSE_NOT_FOUND` |
| 12 | `amount=0` 판매 / `refundAmount=0` 취소 | 0원/음수 결제·환불 누적 → 통계 오염 | 400 `INVALID_REQUEST` (DTO `@Positive`) |

**증거**: 위 12개 모두 `SettlementIntegrationTest` 의 개별 `@Test` 로 데이터 셋업 + 응답 assert. `should_apply_half_open_interval_at_month_boundary`, `should_reject_when_cumulative_refund_exceeds_payment` 등 의도가 보이는 네이밍.

**왜 시드 데이터에 추가하지 않았는가**: 시드는 검증표(creator-1 / 2025-03 = 120,000)의 일관성 데모용. 엣지 시나리오 데이터를 섞으면 시연 동선이 어지러워진다. 엣지케이스 데이터는 테스트 내부에서만 격리해 주입.

---

## 미구현 / 제약사항

작업지시서 §1.5 우선순위에 따라 **1~3순위(정산 정확성·API·README)**를 완벽히 끝낸 뒤 가산점 1개만 깊게 했다.

### 구현된 가산점
- ✅ `SettlementSnapshot` + `UNIQUE(creatorId, periodYearMonth)` (중복 정산 방지)
- ✅ `PENDING → CONFIRMED → PAID` 상태 전이
- ✅ `CommissionRateProvider.rateAt(date)` 추상화로 수수료율 변경 가능성 설계 반영

### 의도적으로 제외한 것 (과설계 방지)
- ❌ 수수료율 변경 이력 테이블 — 인터페이스로만 추상화(이력 추가 시 메서드만 교체)
- ❌ CSV/Excel export
- ❌ QueryDSL — 본 과제 규모에선 JPQL로 충분
- ❌ Docker — 로컬 H2 in-memory로 외부 의존 0

### 범위 선 긋기 (실회계 정책 제외)
실제 회계 시스템 수준의 복합 정책(**PG 수수료, 세금, 정산 보류금, 환원 정산** 등)은 과제 범위 밖으로 두고, 명세에 포함된 판매/환불/수수료 규칙에 집중했다.

### 인증/인가 — 권한 분리는 URL 컨벤션 수준
과제 명시("인증/인가는 간략히 처리해도 무방. userId를 헤더나 파라미터로 전달하는 방식도 허용")에 따라 실제 인증 미들웨어는 생략하고, **역할별 권한은 URL 컨벤션으로만 분리**했다.

| 역할 | 경로 컨벤션 | 호출 주체 |
|---|---|---|
| 운영자(admin) | `/api/admin/...` (운영자 집계 / 정산 확정 / 지급 처리) | 플랫폼 운영자 |
| 크리에이터 | `/api/creators/{creatorId}/...` (자기 판매 내역 / 자기 월별 정산) | 본인 크리에이터 |
| 수강생(student) | **API 호출 주체 아님** — `studentId`는 판매 기록의 데이터 필드일 뿐 | (해당 없음) |

**실서비스 확장 방향**: 게이트웨이 / Spring Security 필터로 JWT 또는 세션 검증 → role claim 으로 admin/creator 게이트 → path variable의 `creatorId`와 토큰의 subject 일치성 검증. 본 과제 범위에선 §1.5(과설계 방지)에 따라 의도적으로 생략.

---

## AI 활용 범위

- **Claude Code** 를 활용해:
  - 정산 규칙 명세 초안 작성(특히 음수 월/월 경계/누적 환불 등 명세가 채우지 않은 지점의 엣지케이스 도출)
  - ERD 초안 및 패키지 구조 제안
  - 작업지시서 v1 → v3 발전(GPT 교차검증 반영)
  - 본 README 의 초안 골격 생성
- **GPT** 를 활용해 작업지시서를 2회 교차검증. 음수 월 수수료 정책(클램프/비환불), 누적 환불 초과 방어, 금액 `long`/시각 `Instant`+KST 분리, 정규화 유지 결정은 GPT 피드백을 반영해 확정.
- **본인이 직접 한 것**: 모든 정책의 최종 결정 / 코드 구현 / 테스트 작성 / 검증표 수치 일치 확인 / 음수 월 등 엣지 케이스 시나리오 직접 추가.
- **검증**: 모든 정산 수치는 단위·통합 테스트 21개로 자동 검증. 검증표 수치는 단위 테스트(`should_match_assignment_validation_table_for_creator1_march`)와 통합 테스트(`should_match_validation_table_for_creator1_march_2025`) 양쪽에서 assert.

---

## 어필 포인트 한 줄 요약

- **환불 귀속**: 본 구현은 과제 명세에 따라 환불을 원판매 월로 소급하지 않고 **환불 발생 월(취소 일시 기준)**에 반영했다.
- **수수료 정책**: 수수료는 **비환불(non-refundable)** 정책으로 가정하여 `commission = max(netSales, 0) × 0.2`로 계산하고, 환불만 있는 달은 수수료 0 · 정산예정 음수(차감)로 처리했다.
- **월 경계**: "말일 23:59:59" 비교 대신 **다음 달 시작 시각 미만(`< 다음달초`)**의 반열린 구간 비교로 밀리초 누락을 방지했다.
- **시각 처리**: 시각은 UTC `Instant`로 저장하고 월 경계 집계는 `Asia/Seoul` 기준으로 변환해 계산.
- **금액 타입**: KRW 원 단위 정산으로 가정하여 부동소수 오차 방지를 위해 금액을 `long`으로 저장(소수점 통화가 아니라 `BigDecimal` 미사용).
- **범위 선 긋기(과설계 인상 방지)**: 실제 회계 시스템 수준의 복합 정책은 과제 범위 밖으로 두고 명세에 포함된 규칙에 집중.
- **명세 공백 인지**: 음수 월 수수료처럼 명세가 명시하지 않은(underspecified) 지점은 임의로 채우지 않고, **정책을 명시적으로 결정하고 근거를 남기는 방식**으로 처리.
