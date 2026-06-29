package com.freightauction.bid.exception;

public class AuctionValidationException extends RuntimeException {

    private final Reason reason;

    public AuctionValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        AUCTION_NOT_SYNCED,
        AUCTION_CLOSED,
        AMOUNT_TOO_HIGH
    }
}