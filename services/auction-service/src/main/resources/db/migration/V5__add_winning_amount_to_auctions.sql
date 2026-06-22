ALTER TABLE auctions
    ADD COLUMN IF NOT EXISTS winning_amount NUMERIC(12, 2);
