package com.freightauction.bid.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.freightauction.bid.exception.AuctionValidationException.class)
    public ProblemDetail handleAuctionValidation(com.freightauction.bid.exception.AuctionValidationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage()
        );
        detail.setTitle("Bid rejected");
        detail.setProperty("reason", ex.getReason().name());
        return detail;
    }
}