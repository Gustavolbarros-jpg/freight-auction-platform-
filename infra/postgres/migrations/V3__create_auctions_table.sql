CREATE TABLE auctions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_id UUID NOT NULL UNIQUE REFERENCES loads(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    winner_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);