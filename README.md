# Plataforma de Negociacao de Fretes

Projeto 09 da disciplina de Sistemas Distribuidos.

## Ideia do Projeto

A plataforma simula um leilao reverso de fretes. Em vez de ganhar quem oferece o maior valor, vence a transportadora que oferece o menor preco para transportar uma carga.

A ideia central e dividir o sistema em servicos independentes:

- um servico para criar cargas e leiloes;
- um servico para receber lances;
- uma fila para ordenar o processamento dos lances;
- um cache para guardar rapidamente o melhor lance atual;
- um servico de notificacao para avisos em tempo real.

## Estado Atual

A entrega atual ja possui a base distribuida funcionando com Docker Compose.

Servicos implementados ate agora:

- `auction-service`: servico Java/Spring Boot responsavel por cargas e leiloes.
- `bid-service`: servico Java/Spring Boot responsavel pelo fluxo de lances.
- `auth-service`: servico Java/Spring Boot responsavel por cadastro, login e tokens JWT.
- `analytics-service`: servico Python/FastAPI responsavel por consultas analiticas do negocio.
- `notification-service`: servico Node.js preparado para notificacoes via WebSocket e Redis Pub/Sub.
- `postgres`: banco relacional usado pelos servicos que precisam persistir dados.
- `mongo`: banco documental usado para eventos de auditoria.
- `rabbitmq`: broker de mensagens usado para processar lances em fila.
- `redis`: cache usado para guardar o melhor lance atual por leilao.
- `prometheus`: coleta metricas dos servicos.
- `grafana`: visualizacao das metricas coletadas.

Tambem existem arquivos de infraestrutura e documentacao em:

- `docker-compose.yml`
- `infra/postgres/migrations`
- `docs`

## Decisoes Tecnologicas

A arquitetura foi pensada como uma plataforma distribuida baseada em microsservicos, com comunicacao HTTP para operacoes sincronas e mensageria para operacoes assincronas.

Tecnologias escolhidas:

- Java 21 com Spring Boot para `auction-service` e `bid-service`: escolha feita por oferecer suporte maduro para APIs REST, validacao de entrada, configuracao por ambiente, integracao com banco relacional, RabbitMQ e Redis.
- Python com FastAPI para `analytics-service`: escolha feita para facilitar agregacoes analiticas, consultas de relatorio e exposicao rapida de endpoints HTTP.
- Node.js para `notification-service`: escolha feita por se encaixar bem no modelo de conexoes WebSocket e notificacoes em tempo real.
- PostgreSQL para dados persistentes: usado para entidades que precisam sobreviver ao ciclo de vida dos servicos, como cargas, leiloes e futuramente historico de lances.
- MongoDB para auditoria: usado para armazenar eventos de negocio em formato documental, como leilao aberto, leilao fechado, lance recebido, lance validado e lance rejeitado.
- RabbitMQ para fila de lances: usado para desacoplar o recebimento HTTP do processamento dos lances e preservar a ordem de chegada no consumidor.
- Redis para estado rapido em memoria: usado para consultar rapidamente o melhor lance atual de cada leilao.
- Prometheus e Grafana para observabilidade: usados para coletar e visualizar metricas tecnicas dos servicos.
- Docker Compose para ambiente local: usado para subir os servicos e dependencias de forma padronizada.

Essas escolhas se encaixam na arquitetura de alto nivel porque separam responsabilidades, permitem evoluir cada servico de forma independente e usam fila/cache para lidar com concorrencia e estado compartilhado.

## Estrutura de Pastas

```text
.
├── docker-compose.yml
├── docs
├── infra
│   └── postgres
│       └── migrations
└── services
    ├── analytics-service
    ├── api-gateway
    ├── auth-service
    ├── auction-service
    ├── bid-service
    └── notification-service
```

## Atendimento da Entrega 1

Checklist da fase de Arquitetura e Escopo:

- Decisao tecnologica documentada: descrita na secao `Decisoes Tecnologicas`.
- Repositorio GitHub organizado: estrutura separada por `services`, `infra` e `docs`.
- README minimo: contem descricao do projeto, tecnologias, estrutura, instrucoes de execucao e fluxo principal.
- Modelagem do estado central em memoria: o melhor lance atual fica no Redis usando a chave `auction:{auctionId}:best_bid`.
- Protocolo de comunicacao definido: endpoints HTTP e mensagens RabbitMQ documentados nas secoes de fluxo e endpoints.
- Servidor base rodando em porta fixada: `auction-service` usa `8081`, `bid-service` usa `8082` e `notification-service` usa `8083`.
- Validacoes basicas: os DTOs usam validacoes como campos obrigatorios, UUIDs e valores numericos para cargas, leiloes e lances.

## Fluxo de Lances

O fluxo principal implementado na entrega e:

```text
POST /v1/bids autenticado -> valida leilao OPEN -> Postgres -> RabbitMQ -> consumer FIFO -> Redis -> WebSocket
```

Na pratica:

1. A transportadora faz login e envia um lance com `Authorization: Bearer <token>` para `POST /v1/bids`.
2. O `bid-service` valida o JWT, extrai a transportadora do token e consulta o `auction-service`.
3. Se o leilao nao existir ou nao estiver `OPEN`, o lance e recusado e nada e publicado na fila.
4. Se o leilao estiver aberto, o lance e salvo no Postgres com status inicial `RECEIVED`.
5. O `bid-service` publica o evento no RabbitMQ.
6. O consumidor do `bid-service` le a fila com apenas um consumidor ativo, preservando a ordem de processamento.
7. O servico compara o novo lance com o melhor lance salvo no Redis.
8. Se o novo lance for menor, ele passa a ser o melhor lance e o canal Redis `bid.validated` e publicado para o `notification-service`.
9. Se o lance for igual ou maior, o melhor lance atual continua e o lance persistido e marcado como `REJECTED`.

O melhor lance fica salvo no Redis na chave:

```text
auction:{auctionId}:best_bid
```

O valor e armazenado no formato:

```text
amount|bidId|carrierId|receivedAt
```

Essa e a modelagem atual do estado central em memoria para a disputa de lances. O Redis guarda apenas o melhor lance atual por leilao, permitindo resposta rapida para consulta e evitando recalcular o vencedor a cada requisicao.

## Protocolo de Comunicacao

O projeto define tres tipos principais de comunicacao:

- HTTP/JSON para chamadas externas e consultas.
- RabbitMQ para eventos assincronos de lances.
- Redis Pub/Sub + WebSocket para notificacoes em tempo real.

### Equivalente ao gRPC sugerido no enunciado

O enunciado sugere gRPC (`grpcio`, `grpc-java` ou equivalente) para os procedimentos remotos da plataforma. Nesta implementacao, a escolha equivalente foi usar HTTP/JSON via API Gateway combinado com mensageria, cache atomico e WebSocket:

| Conceito do enunciado | Implementacao neste projeto |
| --- | --- |
| Procedimento remoto `BID` | `POST /v1/bids` via API Gateway |
| Procedimento remoto `STATUS` | `GET /v1/bids/auctions/{auctionId}/best` via API Gateway |
| Chamadas paralelas de multiplas origens | API Gateway + servicos Spring Boot aceitando requisicoes simultaneas |
| Fila/ordenacao de lances | RabbitMQ (`bid.placed.queue`) com consumidor do `bid-service` |
| Estado central em memoria | Redis, chave `auction:{auctionId}:best_bid` |
| Atualizacao atomica do melhor lance | Script Lua no Redis (`compare_and_set_best_bid.lua`) |
| Broadcast sem polling | Redis Pub/Sub + WebSocket (`notification-service`) |

Assim, mesmo sem servidor gRPC real, os requisitos de procedimento remoto, concorrencia, estado compartilhado e notificacao em tempo real sao atendidos por uma composicao distribuida equivalente.

O fluxo logico dos procedimentos fica:

```text
BID    -> POST /v1/bids -> RabbitMQ -> Redis Lua -> WebSocket
STATUS -> GET /v1/bids/auctions/{auctionId}/best -> Redis
```

Contrato do evento publicado na fila:

```json
{
  "bidId": "6bb45715-266c-4b7a-b111-87e0758ff4b1",
  "auctionId": "11111111-1111-1111-1111-111111111111",
  "carrierId": "22222222-2222-2222-2222-222222222222",
  "amount": 900.00,
  "receivedAt": "2026-06-14T22:23:50Z"
}
```

Configuracao RabbitMQ:

```text
Exchange: bid.exchange
Queue: bid.placed.queue
Routing key: bid.placed
```

Ao fechar um leilao com `PATCH /v1/auctions/{id}/close`, o `auction-service` busca o melhor lance em `bid-service`, grava `winnerCarrierId` e `winningAmount`, muda o status para `CLOSED` e publica o evento `auction.closed` no Redis. Depois disso, novos lances para o mesmo leilao retornam conflito (`409`).

## Autenticacao

Os endpoints de escrita usam JWT:

- `ADMIN`: cria cargas, cria leiloes e fecha leiloes.
- `TRANSPORTADORA`: cria lances.

Criar usuario:

```bash
curl -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Admin","email":"admin@example.com","password":"senha123","role":"ADMIN"}'
```

Login:

```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"senha123"}'
```

A resposta contem o campo `token`. Use-o nos endpoints protegidos:

```bash
Authorization: Bearer <token>
```

## Endpoints Principais

Os exemplos abaixo usam o API Gateway na porta `8080`, que e o ponto de entrada recomendado para clientes externos e para o frontend.

### Criar lance

```http
POST /v1/bids
```

Exemplo:

```bash
curl -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer <token-transportadora>" \
  -H 'Content-Type: application/json' \
  -d '{"auctionId":"11111111-1111-1111-1111-111111111111","amount":900.00}'
```

Corpo JSON:

```json
{
  "auctionId": "11111111-1111-1111-1111-111111111111",
  "amount": 900.00
}
```

Resposta esperada:

```http
202 Accepted
```

O status `202` indica que o lance foi aceito para processamento assincrono na fila.
O `carrierId` nao vem mais no corpo: ele e extraido do JWT da transportadora.

### Consultar melhor lance

```http
GET /v1/bids/auctions/{auctionId}/best
```

Retorna o melhor lance salvo no Redis para o leilao informado.

Exemplo:

```bash
curl http://localhost:8080/v1/bids/auctions/<auction-id>/best \
  -H "Authorization: Bearer <token>"
```

### Criar carga

```bash
curl -X POST http://localhost:8080/v1/loads \
  -H "Authorization: Bearer <token-admin>" \
  -H 'Content-Type: application/json' \
  -d '{"origin":"Recife","destination":"Olinda","description":"Carga teste","weightKg":100,"initialPrice":1000}'
```

### Criar leilao

```bash
curl -X POST http://localhost:8080/v1/auctions \
  -H "Authorization: Bearer <token-admin>" \
  -H 'Content-Type: application/json' \
  -d '{"loadId":"<load-id>","durationMinutes":30}'
```

### Fechar leilao

```bash
curl -X PATCH http://localhost:8080/v1/auctions/<auction-id>/close \
  -H "Authorization: Bearer <token-admin>"
```

A resposta inclui `winnerCarrierId` e `winningAmount` quando existe lance vencedor.

### WebSocket de notificacoes

Conecte em:

```text
ws://localhost:8083?auction=<auction-id>
```

Quando um melhor lance e validado, chega uma mensagem `bid.validated`. Quando um leilao novo e criado, chega `auction.opened`. Quando o leilao fecha, chega uma mensagem `auction.closed`.

Clientes conectados sem parametro de leilao (`ws://localhost:8083`) recebem notificacoes globais. Isso e usado para avisar ADMIN e transportadoras sobre novos leiloes, aviso de encerramento e encerramentos.

Regra de visibilidade das notificacoes:

- `auction.opened`, aviso de "leilao encerrando" e `auction.closed` aparecem para todos os usuarios conectados.
- `bid.validated` aparece para ADMIN e para transportadoras que participam daquele leilao. Uma transportadora que nao deu lance naquele leilao nao recebe notificacao de lance dele.
- As notificacoes aparecem como pop-up em qualquer tela aberta e ficam registradas no sino do topo direito da interface.

## Simulacao via Terminal: BID, STATUS e Concorrencia

Esta secao cobre a simulacao equivalente aos procedimentos remotos `BID` e `STATUS`, usando `curl` pelo terminal.

### 1. Criar usuarios e obter tokens

Crie um ADMIN:

```bash
curl -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Admin Operacional","email":"admin.demo@example.com","password":"senha123","role":"ADMIN"}'
```

Crie duas transportadoras:

```bash
curl -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Transportadora Azul","email":"azul.demo@example.com","password":"senha123","role":"TRANSPORTADORA"}'

curl -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Transportadora Verde","email":"verde.demo@example.com","password":"senha123","role":"TRANSPORTADORA"}'
```

Faça login e salve os tokens:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin.demo@example.com","password":"senha123"}' | jq -r '.token')

AZUL_TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"azul.demo@example.com","password":"senha123"}' | jq -r '.token')

VERDE_TOKEN=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"verde.demo@example.com","password":"senha123"}' | jq -r '.token')
```

> Os exemplos usam `jq` para extrair os tokens. Se nao tiver `jq`, copie o campo `token` manualmente da resposta do login.

### 2. Criar carga e leilao de teste

```bash
LOAD_ID=$(curl -s -X POST http://localhost:8080/v1/loads \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"origin":"Recife - PE","destination":"Salvador - BA","description":"Carga para disputa concorrente","weightKg":1200,"initialPrice":10000}' \
  | jq -r '.id')

AUCTION_ID=$(curl -s -X POST http://localhost:8080/v1/auctions \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"loadId\":\"$LOAD_ID\",\"durationMinutes\":5}" \
  | jq -r '.id')

echo "Leilao criado: $AUCTION_ID"
```

### 3. Consultar STATUS

Antes do primeiro lance, o melhor lance ainda pode nao existir no Redis. Depois de enviar um lance valido:

```bash
curl http://localhost:8080/v1/bids/auctions/$AUCTION_ID/best \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Esse comando equivale ao procedimento remoto `STATUS`: ele devolve o menor lance atual e a transportadora lider.

### 4. Enviar BID pelo terminal

```bash
curl -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer $AZUL_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":9500.00}"
```

Esse comando equivale ao procedimento remoto `BID`. A resposta `202 Accepted` significa que o lance entrou na fila e sera processado assincronamente.

### 5. Simular lances concorrentes

Para disparar lances quase ao mesmo tempo por transportadoras diferentes:

```bash
curl -s -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer $AZUL_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":9300.00}" &

curl -s -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer $VERDE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":9200.00}" &

wait
```

Depois consulte o STATUS:

```bash
curl http://localhost:8080/v1/bids/auctions/$AUCTION_ID/best \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

O menor valor deve ser mantido como lider.

### 6. Simular empate de valor

Dispare dois lances com o mesmo valor:

```bash
curl -s -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer $AZUL_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":9100.00}" &

curl -s -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer $VERDE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":9100.00}" &

wait
```

Regra de desempate:

- o Redis so troca o lider quando `novoValor < valorAtual`;
- se dois lances possuem o mesmo valor, o primeiro processado permanece lider;
- a ordem de chegada/processamento e preservada pela fila RabbitMQ e registrada no Postgres pelo campo `arrival_order`.

### 7. Abrir multiplos paineis de transportadoras

Para validar o broadcast visualmente:

1. Suba o backend com `docker compose up -d --build`.
2. Inicie o frontend:

   ```bash
   cd frontend/bidflow-arena-main
   npm run dev -- --host 0.0.0.0 --port 5173
   ```

3. Abra `http://localhost:5173` em uma janela normal e faça login com a Transportadora Azul.
4. Abra outra janela anonima e faça login com a Transportadora Verde.
5. Em uma terceira janela, faça login como ADMIN.
6. Acesse o mesmo leilao nas duas transportadoras.
7. Envie lances alternados e concorrentes.
8. Verifique:
   - o ranking muda sem atualizar a pagina;
   - a transportadora que perdeu a lideranca recebe alerta de lance superado;
   - o ADMIN recebe notificacao informando o novo menor lance;
   - ao encerrar, o ADMIN ve apenas o campeao do leilao, sem mensagem de vitoria/derrota.

### Analytics

O `analytics-service` expoe consultas agregadas em FastAPI:

```text
GET http://localhost:8085/health
GET http://localhost:8085/docs
GET http://localhost:8085/v1/analytics/auctions
GET http://localhost:8085/v1/analytics/bids
GET http://localhost:8085/v1/analytics/carriers
GET http://localhost:8085/metrics
```

Esses endpoints consultam o Postgres para gerar resumos de leiloes, lances e transportadoras. O endpoint `/metrics` expoe metricas no formato Prometheus.

### Auditoria

Eventos importantes de negocio sao gravados no MongoDB, na collection `events` do banco `audit_db`.

Eventos registrados atualmente:

- `AUCTION_OPENED`
- `AUCTION_CLOSED`
- `BID_RECEIVED`
- `BID_VALIDATED`
- `BID_REJECTED`

Cada evento guarda o tipo, o servico de origem, o `auctionId`, um `payload` com detalhes da operacao e o `timestamp`.

### Observabilidade

Endpoints de metricas:

```text
auction-service: http://localhost:8081/actuator/prometheus
bid-service: http://localhost:8082/actuator/prometheus
auth-service: http://localhost:8084/actuator/prometheus
analytics-service: http://localhost:8085/metrics
```

Ferramentas:

```text
Prometheus: http://localhost:9090
Grafana: http://localhost:3000
usuario: admin
senha: admin
```

## Como Rodar

Para subir a infraestrutura e os servicos principais:

```bash
docker compose up --build
```

Para subir apenas o fluxo de lances:

```bash
docker compose up --build redis rabbitmq bid-service
```

RabbitMQ Management:

```text
http://localhost:15672
usuario: user
senha: password
```

Portas principais:

- `auction-service`: `8081`
- `bid-service`: `8082`
- `notification-service`: `8083`
- `auth-service`: `8084`
- `analytics-service`: `8085`
- `api-gateway`: `8080`
- `postgres`: `5433` no host, `5432` dentro do Docker
- `mongo`: `27017`
- `prometheus`: `9090`
- `grafana`: `3000`
- `rabbitmq`: `5672`
- `rabbitmq-management`: `15672`
- `redis`: `6379`

## Validacao Feita

O fluxo do `bid-service` foi validado com Docker:

- Redis e RabbitMQ subiram saudaveis.
- MongoDB subiu saudavel e recebeu eventos de auditoria.
- Postgres subiu saudavel e recebeu as tabelas de usuarios, cargas, leiloes e lances.
- `auth-service` subiu na porta `8084` e gerou JWT para `ADMIN` e `TRANSPORTADORA`.
- `bid-service` subiu na porta `8082`.
- `analytics-service` subiu na porta `8085` e expos endpoints de resumo e metricas.
- Prometheus e Grafana subiram para observabilidade local.
- `POST /v1/bids` sem token retornou erro; com token de transportadora retornou `202 Accepted`.
- Lances para leilao fechado retornaram `409`.
- A mensagem foi processada pelo consumer via RabbitMQ.
- O lance aceito foi persistido na tabela `bids`.
- O melhor lance foi salvo no Redis.
- `GET /v1/bids/auctions/{auctionId}/best` retornou o menor lance.
- Em caso de empate, o primeiro lance processado foi mantido.
- `PATCH /v1/auctions/{id}/close` gravou vencedor e valor vencedor no leilao.
- Eventos `bid.validated` e `auction.closed` chegaram ao `notification-service` via Redis Pub/Sub/WebSocket.
- Eventos de auditoria foram gravados no MongoDB para lances e leiloes.

## Documentacao

- `docs/entrega-2.md`: resumo final da Entrega 2.
- `docs/entrega-2-plano-execucao.md`: plano de execucao usado para organizar as prioridades da Entrega 2.
- `docs/git-workflow.md`: fluxo de branches e commits.
- `docs/run-locally.md`: orientacoes para rodar localmente.
- `docs/bid-service-implementation.md`: detalhes da implementacao do `bid-service`.
- `docs/bid-service-docker-handoff.md`: pendencias e orientacoes para completar/validar Docker.

## Proximos Passos

Itens que podem entrar nas proximas entregas:

- adicionar um API Gateway para centralizar autenticacao e roteamento;
- evoluir autorizacao com refresh token e revogacao;
- adicionar Testcontainers para validar Postgres/RabbitMQ/Redis em pipeline;
- criar frontend e paineis de acompanhamento dos leiloes;
- evoluir dashboards do Grafana com metricas de negocio e alertas.
