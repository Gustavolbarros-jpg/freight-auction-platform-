-- Migration exclusiva para testes de integração
-- Cria tabelas referenciadas pelas FKs da V4 sem as constraints de negócio

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255),
    role VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS auctions (
    id UUID PRIMARY KEY,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
);