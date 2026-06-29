# Freight Auction Platform

Plataforma distribuida de leilao reverso de fretes. O vencedor de cada leilao e a transportadora que oferece o menor valor valido para transportar uma carga.

O projeto integra backend em microsservicos, frontend web, fila, cache, bancos de dados, notificacoes em tempo real, auditoria e observabilidade com Prometheus e Grafana.

## Sumario

- [Arquitetura](#arquitetura)
- [Servicos](#servicos)
- [Como rodar](#como-rodar)
- [Acessos locais](#acessos-locais)
- [Fluxo principal](#fluxo-principal)
- [Autenticacao e perfis](#autenticacao-e-perfis)
- [Notificacoes em tempo real](#notificacoes-em-tempo-real)
- [Observabilidade](#observabilidade)
- [Testes](#testes)
- [Estrutura do repositorio](#estrutura-do-repositorio)
- [Status da entrega](#status-da-entrega)

## Arquitetura

A aplicacao usa comunicacao sincronica por HTTP/JSON e comunicacao assincrona por RabbitMQ, Redis Pub/Sub e WebSocket.

```text
Frontend
   |
   v
API Gateway :8080
   |
   +--> auth-service :8084 --------> PostgreSQL
   +--> auction-service :8081 -----> PostgreSQL / MongoDB / Redis / RabbitMQ
   +--> bid-service :8082 ---------> PostgreSQL / Redis / RabbitMQ / MongoDB
   +--> analytics-service :8085 ---> PostgreSQL / MongoDB

RabbitMQ -> bid-service consumer -> Redis best bid -> Redis Pub/Sub -> notification-service :8083 -> WebSocket

Prometheus :9090 -> coleta metricas
Grafana :3001 -> dashboards provisionados
```

Principais decisoes:

- `PostgreSQL`: dados transacionais, como usuarios, cargas, leiloes e lances.
- `MongoDB`: auditoria de eventos de negocio.
- `RabbitMQ`: fila para processar lances em ordem.
- `Redis`: melhor lance atual e Pub/Sub de eventos.
- `WebSocket`: notificacoes em tempo real.
- `Prometheus` e `Grafana`: metricas, saude e visualizacao operacional.
- `Docker Compose`: ambiente local reproduzivel.

## Servicos

| Servico | Tecnologia | Responsabilidade |
| --- | --- | --- |
| `frontend` | React/TanStack Start | Interface web para ADMIN e TRANSPORTADORA |
| `api-gateway` | Java 21/Spring Boot | Entrada unica HTTP, roteamento, CORS e JWT |
| `auth-service` | Java 21/Spring Boot | Login, cadastro, perfil e emissao de JWT |
| `auction-service` | Java 21/Spring Boot | Cargas, leiloes, fechamento e vencedor |
| `bid-service` | Java 21/Spring Boot | Recebimento, validacao e processamento de lances |
| `notification-service` | Node.js | WebSocket, Redis Pub/Sub, notificacoes e metricas |
| `analytics-service` | Python/FastAPI | Agregacoes de leiloes, lances e transportadoras |
| `postgres` | PostgreSQL | Banco relacional |
| `mongo` | MongoDB | Auditoria |
| `rabbitmq` | RabbitMQ | Mensageria de lances |
| `redis` | Redis | Cache e Pub/Sub |
| `prometheus` | Prometheus | Coleta de metricas |
| `grafana` | Grafana | Dashboards |
| `k6` | Grafana k6 | Testes de carga via profile |

## Como rodar

Requisitos:

- Docker
- Docker Compose

Subir todo o ambiente:

```bash
docker compose up --build
```

Subir em segundo plano:

```bash
docker compose up -d --build
```

Parar:

```bash
docker compose down
```

Parar removendo volumes:

```bash
docker compose down -v
```

Ver logs:

```bash
docker compose logs -f api-gateway auction-service bid-service notification-service
```

## Acessos locais

| Recurso | URL |
| --- | --- |
| Frontend | `http://localhost:5173` |
| API Gateway | `http://localhost:8080` |
| Auction Service | `http://localhost:8081` |
| Bid Service | `http://localhost:8082` |
| Notification Service | `http://localhost:8083` |
| Auth Service | `http://localhost:8084` |
| Analytics Service | `http://localhost:8085` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` |
| RabbitMQ Management | `http://localhost:15672` |

Credenciais locais:

```text
Grafana:
usuario: admin
senha: admin

RabbitMQ:
usuario: user
senha: password
```

Portas de infraestrutura:

| Componente | Porta |
| --- | --- |
| PostgreSQL | `5433` no host, `5432` no container |
| MongoDB | `27017` |
| Redis | `6379` |
| RabbitMQ AMQP | `5672` |

## Fluxo principal

O fluxo de lance implementado e:

```text
POST /v1/bids
  -> API Gateway valida JWT
  -> bid-service valida leilao e transportadora
  -> bid-service persiste lance recebido no PostgreSQL
  -> RabbitMQ ordena processamento
  -> consumer compara lance com melhor valor no Redis usando Lua atomico
  -> bid-service marca lance como VALIDATED ou REJECTED
  -> Redis Pub/Sub publica evento
  -> notification-service envia WebSocket
  -> frontend atualiza ranking, pop-up e sino
```

Estado central em memoria:

```text
auction:{auctionId}:best_bid
```

Formato do valor no Redis:

```text
amount|bidId|carrierId|receivedAt
```

Regra do leilao reverso:

- vence o menor lance;
- empate mantem o primeiro lance processado;
- lance igual ou maior que o menor lance atual e rejeitado;
- leilao fechado nao aceita novos lances.

## Autenticacao e perfis

Perfis:

- `ADMIN`: cria cargas, cria leiloes, acompanha todos os leiloes, fecha leiloes e recebe todas as notificacoes.
- `TRANSPORTADORA`: visualiza leiloes, envia lances e recebe notificacoes dos leiloes em que participa.

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

Use o token retornado:

```text
Authorization: Bearer <token>
```

Perfil:

- nome pode ser alterado em configuracoes;
- email aparece no perfil como campo nao editavel;
- salvar perfil exige clicar em salvar.

## Endpoints principais

Os exemplos abaixo usam o API Gateway (`localhost:8080`).

Criar carga:

```bash
curl -X POST http://localhost:8080/v1/loads \
  -H "Authorization: Bearer <token-admin>" \
  -H 'Content-Type: application/json' \
  -d '{"origin":"Recife - PE","destination":"Salvador - BA","description":"Carga teste","weightKg":1200,"initialPrice":10000}'
```

Criar leilao:

```bash
curl -X POST http://localhost:8080/v1/auctions \
  -H "Authorization: Bearer <token-admin>" \
  -H 'Content-Type: application/json' \
  -d '{"loadId":"<load-id>","durationMinutes":30}'
```

Enviar lance:

```bash
curl -X POST http://localhost:8080/v1/bids \
  -H "Authorization: Bearer <token-transportadora>" \
  -H 'Content-Type: application/json' \
  -d '{"auctionId":"<auction-id>","amount":9000.00}'
```

Consultar menor lance atual:

```bash
curl http://localhost:8080/v1/bids/auctions/<auction-id>/best \
  -H "Authorization: Bearer <token>"
```

Fechar leilao:

```bash
curl -X PATCH http://localhost:8080/v1/auctions/<auction-id>/close \
  -H "Authorization: Bearer <token-admin>"
```

Analytics:

```text
GET /v1/analytics/auctions
GET /v1/analytics/bids
GET /v1/analytics/carriers
```

## Notificacoes em tempo real

WebSocket:

```text
ws://localhost:8083?auction=<auction-id>
```

Tambem ha notificacoes globais para clientes conectados sem filtro de leilao.

Eventos:

- `auction.opened`: novo leilao criado.
- `bid.validated`: novo menor lance aceito.
- `auction.closing`: aviso de leilao encerrando.
- `auction.closed`: leilao encerrado e vencedor definido.

Regras de visibilidade:

- ADMIN recebe todas as notificacoes.
- TRANSPORTADORA recebe notificacoes globais e notificacoes dos leiloes em que participa.
- Uma transportadora nao recebe notificacao de lance de leilao em que nao participa.
- Pop-ups aparecem em qualquer tela.
- Notificacoes ficam armazenadas no sino do topo direito.
- Pop-ups podem ser fechados manualmente.

## Observabilidade

Prometheus coleta os seguintes alvos:

| Job | Endpoint interno |
| --- | --- |
| `auction-service` | `/actuator/prometheus` |
| `bid-service` | `/actuator/prometheus` |
| `auth-service` | `/actuator/prometheus` |
| `api-gateway` | `/actuator/prometheus` |
| `analytics-service` | `/metrics` |
| `notification-service` | `/metrics` |

Endpoints uteis no host:

```text
Prometheus: http://localhost:9090
Grafana:    http://localhost:3001
```

Dashboard provisionado:

```text
infra/grafana/provisioning/dashboards/freight-auction.json
```

Datasource provisionado:

```text
infra/grafana/provisioning/datasources/prometheus.yml
```

Metricas cobertas:

- saude dos servicos;
- requisicoes HTTP;
- latencia;
- erros;
- metricas JVM;
- metricas de lances;
- eventos e conexoes WebSocket do `notification-service`;
- rotas do `api-gateway`;
- agregacoes do `analytics-service`.

## Testes

Testes unitarios e de integracao por servico:

```bash
cd services/auction-service && mvn test
cd services/bid-service && mvn test
cd services/auth-service && mvn test
cd services/api-gateway && mvn test
cd services/notification-service && npm test
cd services/analytics-service && pytest
```

Teste E2E:

```bash
tests/e2e/run-flow.sh
```

Testes de carga com k6:

```bash
docker compose --profile k6 run --rm k6 run /scripts/scenarios/smoke.js
docker compose --profile k6 run --rm k6 run /scripts/scenarios/stress.js
docker compose --profile k6 run --rm k6 run /scripts/scenarios/spike.js
docker compose --profile k6 run --rm k6 run /scripts/scenarios/soak.js
```

Validações realizadas durante a entrega:

- fluxo de autenticacao com JWT;
- criacao de carga e leilao;
- envio de lances validos e invalidos;
- concorrencia de lances;
- fechamento de leilao com vencedor;
- rejeicao de lances apos fechamento;
- persistencia em PostgreSQL;
- auditoria em MongoDB;
- cache de melhor lance em Redis;
- fila RabbitMQ;
- Redis Pub/Sub e WebSocket;
- dashboards Grafana e targets Prometheus;
- frontend com login, dashboard, sala de leilao, historico, admin e configuracoes.

## Estrutura do repositorio

```text
.
├── docker-compose.yml
├── docs
├── frontend
│   └── bidflow-arena-main
├── infra
│   ├── grafana
│   ├── mongodb
│   └── prometheus
├── services
│   ├── analytics-service
│   ├── api-gateway
│   ├── auth-service
│   ├── auction-service
│   ├── bid-service
│   └── notification-service
└── tests
    ├── e2e
    └── k6
```

## Documentacao complementar

- `INTEGRACAO_FRONTEND.md`: detalhes da integracao do frontend.
- `MERGE_PR_COMMANDS.md`: comandos historicos de merge usados durante a entrega.
- `docs/run-locally.md`: guia de execucao local.
- `docs/test-dependencies.md`: dependencias de testes.
- `docs/git-workflow.md`: fluxo de Git.
- `docs/entrega-2.md`: resumo da entrega anterior.

## Status da entrega

Implementado:

- arquitetura distribuida com microsservicos;
- API Gateway;
- autenticacao JWT;
- leilao reverso de fretes;
- processamento assincrono de lances;
- controle atomico do menor lance via Redis Lua;
- notificacoes em tempo real;
- frontend funcional para ADMIN e TRANSPORTADORA;
- dashboard administrativo e dashboard de transportadora;
- historico de leilao;
- configuracoes de perfil;
- auditoria em MongoDB;
- analytics em FastAPI;
- observabilidade com Prometheus e Grafana;
- testes automatizados e scripts de carga.

Observacao sobre gRPC:

O enunciado sugeria gRPC ou equivalente. Esta implementacao usa HTTP/JSON via API Gateway, RabbitMQ, Redis e WebSocket como composicao equivalente para procedimentos remotos, concorrencia, ordenacao, estado compartilhado e broadcast em tempo real.
