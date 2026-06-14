# Bid Service - Implementacao Atual

Este documento resume o que foi implementado na branch `feature/bid-service`.

## Objetivo

Criar o primeiro fluxo do `bid-service` para a Entrega 1:

```text
POST /bids -> RabbitMQ -> consumer FIFO -> Redis
```

O requisito principal da entrega e garantir que os lances sejam processados em ordem de chegada. Para isso, o servico publica lances no RabbitMQ e o consumer processa a fila com apenas um consumidor.

## Estrutura Criada

O projeto Spring Boot foi criado em:

```text
services/bid-service
```

Configuracao principal:

- Java 21
- Spring Boot 4.0.7
- Maven
- porta `8082`
- Spring WebMVC
- Validation
- Spring AMQP
- Spring Data Redis

## Endpoints

### Criar Lance

```http
POST /bids
```

Exemplo de body:

```json
{
  "auctionId": "11111111-1111-1111-1111-111111111111",
  "carrierId": "22222222-2222-2222-2222-222222222222",
  "amount": 900.00
}
```

Resposta esperada:

```http
202 Accepted
```

O endpoint retorna `QUEUED`, porque o lance foi aceito para processamento na fila. O vencedor nao e decidido diretamente na requisicao HTTP.

### Consultar Melhor Lance

```http
GET /bids/auctions/{auctionId}/best
```

Esse endpoint consulta o melhor lance salvo no Redis.

Se ainda nao existir lance para o leilao, retorna `404`.

## RabbitMQ

O servico declara automaticamente:

```text
Exchange: bid.exchange
Queue: bid.placed.queue
Routing key: bid.placed
```

Nao e necessario criar a fila manualmente no painel do RabbitMQ. Quando o `bid-service` sobe conectado ao RabbitMQ, o Spring declara a exchange, a fila e o binding.

## FIFO

O consumer foi configurado com concorrencia 1:

```text
concurrentConsumers = 1
maxConcurrentConsumers = 1
```

Isso faz com que os eventos sejam processados um por vez, respeitando a ordem da fila.

## Redis

O melhor lance de cada leilao e salvo na chave:

```text
auction:{auctionId}:best_bid
```

O valor atual e salvo como string simples:

```text
amount|bidId|carrierId|receivedAt
```

Regra implementada:

- se nao existe melhor lance, salva o primeiro;
- se o novo lance for menor, substitui;
- se o novo lance for igual, nao substitui;
- se o novo lance for maior, nao substitui.

Essa regra preserva o desempate por ordem de chegada: em caso de empate, fica salvo quem foi processado primeiro pela fila.

## Docker Compose

O `bid-service` foi adicionado ao `docker-compose.yml`.

Ele depende de:

- RabbitMQ saudavel;
- Redis saudavel.

Variaveis configuradas no Compose:

```text
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=user
RABBITMQ_PASSWORD=password
REDIS_HOST=redis
REDIS_PORT=6379
```

## Validacoes Feitas

Foram executados:

```bash
./mvnw test
```

Resultado:

```text
BUILD SUCCESS
Tests run: 1, Failures: 0, Errors: 0
```

Tambem foi executado:

```bash
docker compose config
```

Resultado: compose valido.

## Observacao

O teste automatizado atual apenas sobe o contexto Spring. O teste real do fluxo com RabbitMQ e Redis ainda precisa ser feito com Docker rodando.
