# Entrega 2 — Validação de Leilão no bid-service

## Contexto

Antes desta entrega, o `bid-service` aceitava qualquer `auctionId` sem verificar
se o leilão existia ou estava aberto. Isso tornava a validação de negócio inexistente
no caminho crítico de aceite de lances.

## Solução implementada

### Fluxo geral

```
auction-service                   RabbitMQ                  bid-service
     |                                |                          |
  create() ──► auction.opened ──────► fila durable ──────► AuctionEventConsumer
     |                                                           │
  close()  ──► auction.closed ──────► fila durable ──────► AuctionEventConsumer
                                                                 │
                                                         Redis local (bid-service)
                                                          auction:{id}:status
                                                          auction:{id}:initial_price
                                                                 │
                                                         BidService.placeBid()
                                                          lê cache → valida → publica
```

### Arquivos novos/modificados

**auction-service**
- `AuctionRabbitMqConfig` — declara `auction.exchange`, filas `auction.opened.queue`
  e `auction.closed.queue` (ambas durable).
- `AuctionOpenedEvent`, `AuctionClosedEvent` — records de evento.
- `AuctionEventPublisher` — publica os eventos via `RabbitTemplate`.
- `AuctionService` — modificado para chamar o publisher após `create()` e `close()`.

**bid-service**
- `RabbitMqConfig` — adicionados beans para `auction.exchange` e suas duas filas.
- `AuctionOpenedEvent`, `AuctionClosedEvent` (pacote `auction/`) — mirrors dos
  records do produtor, usados apenas para desserialização.
- `AuctionCacheService` — lê e escreve o estado do leilão no Redis local.
- `AuctionEventConsumer` — `@RabbitListener` que atualiza o cache ao receber eventos.
- `BidService` — modificado: consulta `AuctionCacheService` antes de publicar o lance.
- `AuctionValidationException` — exceção de domínio com enum `Reason`.
- `GlobalExceptionHandler` — mapeia a exceção para HTTP 422 com campo `reason`.

---

## Decisão de design: fail-closed na janela de consistência eventual

### O problema

Há uma janela de tempo entre o `auction-service` salvar o leilão e publicar
`auction.opened`, e o `bid-service` consumir esse evento e gravar no Redis local.
Durante essa janela (tipicamente milissegundos), um lance chegando ao `bid-service`
não encontra nenhuma chave `auction:{id}:status` no cache.

### A decisão

**Rejeitamos o lance com HTTP 422 e `reason: AUCTION_NOT_SYNCED`.**

Esse é o padrão *fail-closed*: na dúvida, nega. Justificativas:

1. **Segurança de negócio**: aceitar um lance para um leilão inexistente ou ainda
   não sincronizado pode gerar dados inconsistentes difíceis de corrigir.
2. **Raridade**: a janela dura milissegundos em condições normais. Na prática,
   um usuário que acabou de criar um leilão e imediatamente tenta dar um lance
   via API é um caso de integração/teste, não de uso real.
3. **Mensagem clara**: o cliente recebe `"Auction not yet synchronized — please retry
   in a moment"`, que é acionável.
4. **Simplicidade**: não exige chamada HTTP síncrona de volta ao `auction-service`,
   mantendo os serviços desacoplados em runtime.

### Alternativa considerada e descartada: cache-aside com fallback HTTP

A alternativa seria: se `isUnknown(auctionId)` → fazer GET síncrono em
`auction-service/auctions/{id}` → gravar no cache → continuar com a validação.

**Por que não foi implementado agora:**
- Reintroduz acoplamento de runtime entre os dois serviços — exatamente o que
  a arquitetura assíncrona elimina.
- Exige tratamento de timeout, circuit breaker e retry no `bid-service`.
- Adiciona complexidade desnecessária dado o baixo impacto do caso de uso.

**Quando valeria a pena (trabalho futuro):** se a janela de inconsistência se mostrar
problemática em produção (ex: integrações que criam leilão e lance na mesma requisição),
o fallback pode ser adicionado de forma cirúrgica em `AuctionCacheService.getStatus()`
sem alterar a interface pública do serviço.

---

## Propriedades garantidas

| Propriedade    | Mecanismo                                                        |
|----------------|------------------------------------------------------------------|
| Idempotência   | `SET` no Redis sobrescreve o mesmo valor sem efeito colateral    |
| Durabilidade   | Filas declaradas com `durable=true` — eventos sobrevivem restart |
| Ordem          | Garantida por canal RabbitMQ para eventos do mesmo leilão        |
| Desacoplamento | `bid-service` não tem dependência HTTP em `auction-service`      |

---

## Respostas HTTP do bid-service após esta entrega

| Situação                          | HTTP | `reason`                |
|-----------------------------------|------|-------------------------|
| Leilão ainda não sincronizado     | 422  | `AUCTION_NOT_SYNCED`    |
| Leilão fechado                    | 422  | `AUCTION_CLOSED`        |
| Lance ≥ preço inicial             | 422  | `AMOUNT_TOO_HIGH`       |
| Lance válido                      | 202  | — (retorna `bidId`)     |