CREATE TABLE loads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origin VARCHAR(160) NOT NULL,
    destination VARCHAR(160) NOT NULL,
    description TEXT,
    weight_kg NUMERIC(10, 2) NOT NULL CHECK (weight_kg > 0),
    initial_price NUMERIC(12, 2) NOT NULL CHECK (initial_price > 0),
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);