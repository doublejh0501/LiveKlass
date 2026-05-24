package com.futurescholae.liveklass.settlement.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),

    SALE_NOT_FOUND(HttpStatus.NOT_FOUND),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND),

    DUPLICATE_SETTLEMENT(HttpStatus.CONFLICT),
    INVALID_SETTLEMENT_STATE(HttpStatus.CONFLICT),

    REFUND_EXCEEDS_PAYMENT(HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
