package com.futurescholae.liveklass.settlement.service;

import com.futurescholae.liveklass.settlement.domain.CancelRecord;
import com.futurescholae.liveklass.settlement.domain.SaleRecord;
import com.futurescholae.liveklass.settlement.exception.BusinessException;
import com.futurescholae.liveklass.settlement.exception.ErrorCode;
import com.futurescholae.liveklass.settlement.repository.CancelRecordRepository;
import com.futurescholae.liveklass.settlement.repository.SaleRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancellationService {

    private final CancelRecordRepository cancelRecordRepository;
    private final SaleRecordRepository saleRecordRepository;

    /**
     * 취소(환불) 등록. 누적 환불액이 원결제 금액을 초과하면 422.
     */
    @Transactional
    public CancelRecord register(String cancelId, String saleId, long refundAmount, Instant cancelledAt) {
        if (refundAmount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Refund amount must be positive");
        }

        SaleRecord sale = saleRecordRepository.findById(saleId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SALE_NOT_FOUND,
                        "Sale not found: " + saleId));

        long existingRefund = cancelRecordRepository.sumRefundAmountBySaleId(saleId);
        long cumulative = existingRefund + refundAmount;
        if (cumulative > sale.getAmount()) {
            throw new BusinessException(
                    ErrorCode.REFUND_EXCEEDS_PAYMENT,
                    String.format(
                            "Cumulative refund %d exceeds original payment %d (existing=%d, requested=%d)",
                            cumulative, sale.getAmount(), existingRefund, refundAmount));
        }

        String id = (cancelId != null && !cancelId.isBlank()) ? cancelId : UUID.randomUUID().toString();
        CancelRecord cancel = CancelRecord.builder()
                .id(id)
                .saleId(saleId)
                .refundAmount(refundAmount)
                .cancelledAt(cancelledAt)
                .build();
        return cancelRecordRepository.save(cancel);
    }
}
