-- A constraint antiga (UNIQUE inline em load_id, criada na V3) permite só 1 auction
-- por load para sempre, independente do status. A regra de negócio real é outra:
-- pode haver várias auctions na mesma load (histórico), mas só UMA pode estar OPEN
-- por vez. Por isso trocamos a UNIQUE total por um índice único PARCIAL.

ALTER TABLE auctions DROP CONSTRAINT auctions_load_id_key;

CREATE UNIQUE INDEX uq_auctions_load_id_open
    ON auctions (load_id)
    WHERE status = 'OPEN';