# Bid Service - Proximos Passos de Docker e Validacao

Este documento e um handoff para quem vai completar a validacao da Entrega 1.

## Objetivo

Validar o fluxo distribuido real:

```text
HTTP -> bid-service -> RabbitMQ -> consumer FIFO -> Redis
```

## 1. Subir o Projeto

Na raiz do repositorio:

```bash
docker compose up --build
```

Conferir se estes servicos ficam de pe:

```bash
docker compose ps
```

Servicos importantes para este teste:

- `rabbitmq`
- `redis`
- `bid-service`

## 2. Conferir RabbitMQ

Abrir o painel:

```text
http://localhost:15672
```

Credenciais:

```text
user / password
```

Verificar se existem:

```text
Exchange: bid.exchange
Queue: bid.placed.queue
Routing key: bid.placed
```

Se nao aparecerem logo no inicio, enviar um lance e conferir novamente.

## 3. Enviar Lances

Enviar primeiro lance:

```bash
curl -X POST http://localhost:8082/bids \
  -H "Content-Type: application/json" \
  -d '{
    "auctionId": "11111111-1111-1111-1111-111111111111",
    "carrierId": "22222222-2222-2222-2222-222222222222",
    "amount": 1000.00
  }'
```

Enviar lance menor:

```bash
curl -X POST http://localhost:8082/bids \
  -H "Content-Type: application/json" \
  -d '{
    "auctionId": "11111111-1111-1111-1111-111111111111",
    "carrierId": "33333333-3333-3333-3333-333333333333",
    "amount": 900.00
  }'
```

Consultar melhor lance:

```bash
curl http://localhost:8082/bids/auctions/11111111-1111-1111-1111-111111111111/best
```

Resultado esperado: o melhor lance deve ser `900.00`.

## 4. Testar Empate

Enviar dois lances com o mesmo valor:

```bash
curl -X POST http://localhost:8082/bids \
  -H "Content-Type: application/json" \
  -d '{
    "auctionId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "carrierId": "11111111-1111-1111-1111-111111111111",
    "amount": 800.00
  }'
```

```bash
curl -X POST http://localhost:8082/bids \
  -H "Content-Type: application/json" \
  -d '{
    "auctionId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "carrierId": "22222222-2222-2222-2222-222222222222",
    "amount": 800.00
  }'
```

Consultar:

```bash
curl http://localhost:8082/bids/auctions/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/best
```

Resultado esperado: deve permanecer o primeiro `carrierId`, porque empate nao substitui o lance ja salvo.

## 5. Conferir Redis Diretamente

Opcionalmente, conferir a chave no Redis:

```bash
docker compose exec redis redis-cli
```

Dentro do Redis:

```bash
GET auction:11111111-1111-1111-1111-111111111111:best_bid
```

Formato esperado:

```text
900.00|bidId|carrierId|receivedAt
```

## 6. Possiveis Ajustes Se Algo Falhar

Se `bid-service` nao conectar no RabbitMQ:

- conferir se `rabbitmq` esta healthy;
- conferir as variaveis `RABBITMQ_HOST`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`;
- ver logs com `docker compose logs bid-service`.

Se `bid-service` nao conectar no Redis:

- conferir se `redis` esta healthy;
- conferir `REDIS_HOST=redis`;
- ver logs com `docker compose logs bid-service`.

Se o melhor lance nao mudar:

- verificar se o lance novo tem valor menor;
- lembrar que valor igual nao substitui, por regra de desempate FIFO.

## 7. Depois da Validacao

Se o fluxo funcionar, o PR ja cobre o nucleo da Entrega 1:

```text
RabbitMQ + Redis + processamento FIFO
```

Melhorias futuras podem ficar em outra branch:

- persistir lances no PostgreSQL;
- publicar evento `bid.validated`;
- integrar notification-service ao evento validado;
- criar testes automatizados de fluxo;
- adicionar endpoint de historico de lances.
