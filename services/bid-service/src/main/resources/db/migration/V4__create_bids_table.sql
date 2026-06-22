CREATE SEQUENCE IF NOT EXISTS bid_arrival_order_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS bids (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL REFERENCES auctions(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    arrival_order BIGINT NOT NULL DEFAULT nextval('bid_arrival_order_seq'),
    status VARCHAR(30) NOT NULL CHECK (status IN ('RECEIVED', 'VALIDATED', 'REJECTED', 'WINNING')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bids_auction_arrival ON bids (auction_id, arrival_order);
