# Comandos para push e PR

## 1. Push da branch

```bash
git push origin feature/raceCondition
```

## 2. Criar o PR

```bash
gh pr create \
  --title "fix: resolve race conditions no bid e auction service" \
  --base develop \
  --head feature/raceCondition \
  --body "## Summary
- Lua script atomico no Redis elimina race condition no BestBidService (GET + compare + SET indivisivel)
- SELECT FOR UPDATE no close() do AuctionService via findAuctionForUpdateOrThrow (lock pessimista)
- Redis pub/sub para canal auction.opened integrado com o notification service
- broadcastAll no WebSocket para eventos globais (leilao aberto)

## Conflitos resolvidos no merge com develop
- application.properties: baseline-version=1 + configs RabbitMQ, Swagger, MongoDB, Prometheus
- AuctionService: combina audit + RabbitMQ (develop) com Redis pub/sub + lock pessimista (raceCondition)
- BestBidService: mantem Lua script atomico
- WebSocketController, index.js, NotificationService: suporte ao canal auction.opened

## Test plan
- [ ] Testar criacao de leilao e verificar evento auction.opened chega no notification service via WebSocket
- [ ] Testar fechamento concorrente do mesmo leilao (deve aceitar so o primeiro)
- [ ] Testar bids concorrentes no mesmo leilao (so o menor deve vencer)"
```

## Alternativa sem gh CLI

Se nao tiver o gh instalado, cria o PR pelo navegador:

https://github.com/Gustavolbarros-jpg/freight-auction-platform-/compare/develop...feature/raceCondition
