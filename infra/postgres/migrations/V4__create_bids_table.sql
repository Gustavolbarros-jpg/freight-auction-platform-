CREATE TABLE bids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id UUID NOT NULL REFERENCES auctions(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    arrival_order BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('RECEIVED', 'VALIDATED', 'REJECTED', 'WINNING')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);