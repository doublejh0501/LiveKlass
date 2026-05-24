package com.futurescholae.liveklass.settlement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 수수료율 조회 추상화.
 *
 * 현재는 고정값(application.yml: settlement.commission-rate)을 반환하지만,
 * 시점(date)을 받도록 시그니처를 분리해두면 향후 "수수료율 변경 이력 관리"(과거 정산은
 * 당시 수수료율 적용) 가산점 기능 추가 시 이 메서드만 교체하면 된다.
 */
@Component
public class CommissionRateProvider {

    private final BigDecimal defaultRate;

    public CommissionRateProvider(
            @Value("${settlement.commission-rate:0.20}") BigDecimal defaultRate
    ) {
        this.defaultRate = defaultRate;
    }

    public BigDecimal rateAt(LocalDate date) {
        return defaultRate;
    }

    public BigDecimal currentRate() {
        return defaultRate;
    }
}
