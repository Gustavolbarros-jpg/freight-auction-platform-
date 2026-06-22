# Entrega 2 - Plano de Execucao

Este documento detalha **como** implementar cada gap identificado no diagnostico da Entrega 2, na ordem de prioridade. Complementa `docs/entrega-2.md` (que define o escopo); aqui o foco e o "como fazer", com arquivos e pontos de entrada concretos.

---

## 1. Logging estruturado em `auction-service` e `bid-service`

**Problema:** nenhum `Logger`/`@Slf4j` nos servicos Java. So existe log de boot do Spring.

**Como fazer:**

- Adicionar `@Slf4j` (Lombok) ou `private static final Logger log = LoggerFactory.getLogger(Classe.class);` nas classes de `service/` e `controller/`.
- Logar no minimo:
  - No controller: requisicao recebida (metodo, path, parametros relevantes — nao logar dados sensiveis).
  - No service: operacao realizada (ex: "Auction criado: id={}, loadId={}", "Lance aceito: bidId={}, auctionId={}, amount={}").
  - Em qualquer `catch`/excecao de negocio: o motivo da recusa (ex: "Lance recusado: leilao {} fechado").
- Usar como referencia o padrao ja existente em `services/notification-service/src/controller/WebSocketController.js` (loga conexao/desconexao de cliente).
- Opcional, mas recomendado: configurar `logging.pattern.console` no `application.properties` para incluir timestamp e nivel, deixando o log "estruturado" de forma legivel no terminal.
- Onde tocar:
  - `services/auction-service/src/main/java/com/freightauction/auction/service/AuctionService.java`
  - `services/auction-service/src/main/java/com/freightauction/auction/controller/AuctionController.java`
  - `services/bid-service/src/main/java/com/freightauction/bid/service/BidService.java`
  - `services/bid-service/src/main/java/com/freightauction/bid/service/BestBidService.java`
  - `services/bid-service/src/main/java/com/freightauction/bid/controller/BidController.java`

**Critério de pronto:** ao rodar `docker compose up` e bater os endpoints via curl, o console de cada servico mostra linhas claras de requisicao recebida e operacao realizada.

---

## 2. Conectar notificacao em tempo real (publish no Redis)

**Problema:** `notification-service` assina os canais `bid.validated` e `auction.closed` (`services/notification-service/src/index.js:11`), mas nada publica neles. `BestBidService.process()` so faz `SET` de uma chave Redis, nunca `PUBLISH`.

**Como fazer:**

- Em `services/bid-service/src/main/java/com/freightauction/bid/service/BestBidService.java`, dentro do `process()`, quando o novo lance se tornar o melhor (bloco `if (currentBestBid == null || ...)`), alem do `redisTemplate.opsForValue().set(...)`, publicar no canal `bid.validated`:
  ```java
  redisTemplate.convertAndSend("bid.validated", serialize(event));
  ```
  (usar `StringRedisTemplate` ja injetado — ele tem `convertAndSend`).
- Definir um payload claro (JSON é melhor que o formato `|` ja usado para o Redis key, para o `notification-service` conseguir fazer `JSON.parse`). Sugestao: serializar como JSON contendo `auctionId`, `bidId`, `carrierId`, `amount`, `receivedAt`.
- Em `services/auction-service/.../service/AuctionService.java`, no metodo `close()`, depois de salvar o leilao com status `CLOSED`, publicar no canal `auction.closed` (precisa injetar um `StringRedisTemplate` ou `RedisTemplate` no `auction-service` — hoje esse servico nao tem dependencia de Redis, sera preciso adicionar a dependencia `spring-boot-starter-data-redis` e configurar host/porta no `application.properties`, igual ja existe no `bid-service`).
- Do lado do `notification-service`, verificar em `services/notification-service/src/service/NotificationService.js` se o `handleBidEvent` já sabe interpretar o payload publicado (hoje provavelmente so faz `broadcast` do texto recebido — confirmar formato esperado e ajustar se necessario).

**Critério de pronto:** abrir uma conexao WebSocket (`ws://localhost:8083/?auction=<id>`) e, ao enviar um `POST /bids` melhor que o atual, ver a mensagem chegar no client conectado.

---

## 3. `auth-service` minimo

**Problema:** pasta `services/auth-service/` vazia (so `.gitkeep`). Hoje `X-User-Id` é um header arbitrario sem validacao (`AuctionController.java:26`, `:45`) — qualquer requisicao pode alegar ser qualquer usuario.

**Como fazer (escopo minimo, nao precisa ser OAuth completo):**

- Criar projeto Spring Boot (igual `auction-service`/`bid-service`, reaproveitar `pom.xml` como base) com:
  - Entidade `User` (id, email, password hash, role/tipo: embarcador ou transportadora).
  - Endpoint `POST /v1/auth/register` (cria usuario, hash da senha com BCrypt).
  - Endpoint `POST /v1/auth/login` (valida credenciais, devolve um token).
  - Token: o mais simples que atende ao enunciado é JWT (lib `io.jsonwebtoken:jjwt`) assinado com um secret compartilhado via variavel de ambiente. Alternativa ainda mais simples: token opaco salvo em tabela/Redis com TTL.
- Nos outros servicos (`auction-service`, `bid-service`), trocar a leitura do header solto `X-User-Id` por:
  - Um filtro/interceptor que valida o JWT (verifica assinatura e expiracao) e extrai o `userId` dali, OU
  - Uma chamada sincrona ao `auth-service` para validar o token (mais simples de implementar agora, mais lento em produção — aceitavel para a Entrega 2).
- Migration: adicionar tabela `users` em `infra/postgres/migrations/` (ja existe `V1__create_users_table.sql` — confirmar se já cobre isso ou se precisa ajustar).

**Critério de pronto:** sem token valido, `POST /v1/auctions` e `POST /bids` retornam 401. Com token de login, o `createdByUserId`/`carrierId` passa a vir do token, nao de um header livre.

---

## 4. Validar leilao (existe + OPEN) antes de aceitar lance

**Problema:** `BidService.placeBid` (`services/bid-service/.../service/BidService.java:23`) cria e publica o evento sem checar `auctionId`.

**Como fazer:**

- Opcao mais simples para a Entrega 2: `bid-service` faz uma chamada HTTP sincrona ao `auction-service` (`GET /v1/auctions/{id}`) usando `RestClient`/`RestTemplate`/`WebClient`, antes de publicar o evento.
  - Criar um cliente simples, ex: `AuctionClient` em `bid-service`, com a URL do `auction-service` configuravel via `application.properties` (`auction-service.url=${AUCTION_SERVICE_URL:http://localhost:8081}`).
  - Em `BidService.placeBid`, antes de montar o `BidPlacedEvent`: buscar o leilao, se 404 -> lancar erro 404/422; se status != `OPEN` -> lancar erro 409 (conflito).
- Mapear essas excecoes para respostas HTTP claras (criar um `@RestControllerAdvice` ou usar `ResponseStatusException` como ja é feito em `BestBidService.java:39`).

**Critério de pronto:** `POST /bids` com `auctionId` inexistente ou de leilao `CLOSED` retorna erro e **nao** publica nada no RabbitMQ.

---

## 5. Persistir lances no Postgres

**Problema:** nao existe entidade/repositorio `Bid`. Lance hoje só vive no Redis (chave do melhor lance) e passa pela fila — se Redis reiniciar, perde-se o melhor lance e nao ha historico.

**Atencao:** ha trabalho comecado (domain `Bid`/`BidStatus`) na branch `feature/bid-service`, nao commitado. Verificar com `git status`/`git stash list` nessa branch antes de recriar do zero, para nao perder o que ja foi escrito.

**Como fazer:**

- Criar entidade `Bid` em `services/bid-service/src/main/java/com/freightauction/bid/domain/Bid.java` com campos: `id`, `auctionId`, `carrierId`, `amount`, `status` (`BidStatus` ja existe), `receivedAt`.
- Migration: ja existe `infra/postgres/migrations/V4__create_bids_table.sql` — confirmar se o schema bate com a entidade ou precisa de ajuste/nova migration.
- Criar `BidRepository extends JpaRepository<Bid, UUID>`.
- Adicionar `spring-boot-starter-data-jpa` + driver Postgres no `pom.xml` do `bid-service` (hoje ele so tem Rabbit/Redis — confirmar em `services/bid-service/pom.xml`).
- Configurar `spring.datasource.*` e `spring.flyway.*` no `application.properties` do `bid-service`, igual ja esta feito no `auction-service`.
- Decidir **onde** salvar: no `BidService.placeBid` (salva como `PENDING`/`QUEUED` antes de publicar) e/ou no `BidPlacedConsumer.consume` (atualiza status apos processar). Recomendado: salvar no `placeBid` com status inicial, e atualizar o status (ex: `ACCEPTED`/`REJECTED`) no consumer apos o `BestBidService.process`.

**Critério de pronto:** toda chamada `POST /bids` aceita gera uma linha na tabela `bids`; o endpoint de melhor lance continua respondendo via Redis (nao precisa trocar essa parte).

---

## 6. Fechar leilao definindo vencedor e bloqueando novos lances

**Problema:** `AuctionService.close()` (`services/auction-service/.../service/AuctionService.java:58-70`) so muda o status para `CLOSED` e seta `closedAt`. Nao busca o melhor lance nem registra vencedor. E o `bid-service` (apos a task 4) so vai rejeitar lances se conseguir consultar o status atualizado — confirmar que o fluxo de fechamento realmente reflete no `GET /v1/auctions/{id}` antes de liberar essa task como pronta.

**Como fazer:**

- Adicionar campos `winnerCarrierId` e `winningAmount` na entidade `Auction` (migration nova, ex: `V5__add_winner_to_auctions.sql`).
- Em `AuctionService.close()`, antes de salvar como `CLOSED`:
  - Buscar o melhor lance: chamada HTTP ao `bid-service` (`GET /bids/auctions/{auctionId}/best`, ja existe em `BidController.java:38`) **ou** consultar a tabela `bids` direto se preferirem evitar acoplamento sincrono (mas como os servicos sao bancos separados, a chamada HTTP é o caminho mais simples).
  - Setar `winnerCarrierId`/`winningAmount` com o resultado.
- Depois de fechar, publicar no canal `auction.closed` (ja coberto na task 2).
- A task 4 (`bid-service` validando status `OPEN`) ja cobre o bloqueio de novos lances apos fechamento — nao precisa duplicar logica aqui, so garantir que a ordem de implementacao (4 antes de 6, ou as duas testadas juntas) realmente impede a corrida (lance chegando no exato momento do fechamento e aceitavel ter uma janela pequena, nao precisa lock distribuido para a Entrega 2).

**Critério de pronto:** `PATCH /v1/auctions/{id}/close` retorna o leilao com vencedor preenchido; um novo `POST /bids` para esse leilao retorna erro.

---

## 7. Testes de dominio

**Problema:** unicos testes existentes sao os `*ApplicationTests.java` padrao (`contextLoads()`, 13 linhas, sem asserts de negocio).

**Como fazer:**

- `bid-service`:
  - Teste unitario de `BidService.placeBid` mockando `AuctionClient` (task 4) e `BidEventPublisher`, cobrindo: leilao inexistente -> erro; leilao fechado -> erro; leilao aberto -> publica e retorna `ACCEPTED`.
  - Teste unitario de `BestBidService.process` cobrindo: primeiro lance vira o melhor; lance maior nao substitui; lance igual mantem o primeiro (regra de empate).
- `auction-service`:
  - Teste de `AuctionService.close` cobrindo: fechar leilao ja fechado -> erro; fechar leilao aberto -> status muda e vencedor é preenchido (apos task 6).
- Usar JUnit 5 + Mockito (ja deve vir com `spring-boot-starter-test`, presente nos `pom.xml`). Nao precisa de Testcontainers para esta entrega — testes unitarios com mocks bastam para o criterio "regras principais cobertas".

**Critério de pronto:** `./mvnw test` passa em ambos servicos cobrindo as regras acima.

---

## 8. Atualizar README

**Quando fazer:** por ultimo, depois que os fluxos acima estiverem implementados (documentar codigo que ja funciona, nao o planejado).

**Como fazer:**

- Atualizar a secao "Fluxo de Lances" do `README.md` para incluir: validacao de leilao, persistencia em Postgres, fechamento com vencedor, notificacao via WebSocket.
- Adicionar secao "Autenticacao" explicando `POST /v1/auth/register` e `/login`, e que os demais endpoints agora exigem token.
- Adicionar exemplos `curl` para cada endpoint novo/alterado (login, criar lance autenticado, fechar leilao, conectar no WebSocket).
- Revisar se `docker compose up --build` ainda sobe tudo sem ajuste manual (inclui o `auth-service` novo no `docker-compose.yml`).

**Critério de pronto:** alguem de fora do time consegue rodar e testar o fluxo completo so lendo o README.
