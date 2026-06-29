# Freight Auction Platform

Plataforma distribuída de leilão reverso de fretes. O vencedor de cada leilão é a transportadora que oferece o menor valor válido para transportar uma carga.

O projeto integra backend em microsserviços, frontend web, fila, cache, bancos de dados, notificações em tempo real, auditoria e observabilidade com Prometheus e Grafana.

## Sumário

- [Arquitetura](#arquitetura)
- [Serviços](#serviços)
- [Como rodar](#como-rodar)
- [Acessos locais](#acessos-locais)
- [Interface gráfica](#interface-gráfica)
- [Fluxo principal](#fluxo-principal)
- [Controle de concorrência](#controle-de-concorrência)
- [Autenticação e perfis](#autenticação-e-perfis)
- [Notificações em tempo real](#notificações-em-tempo-real)
- [Persistência de dados](#persistência-de-dados)
- [Observabilidade](#observabilidade)
- [Testes](#testes)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Documentação complementar](#documentação-complementar)
- [Status da entrega](#status-da-entrega)

## Arquitetura

A aplicação usa comunicação síncrona por HTTP/JSON e comunicação assíncrona por RabbitMQ, Redis Pub/Sub e WebSocket.

```text
Frontend (React) :5173
   |
   v
API Gateway :8080
   |
   +--> auth-service      :8084 --------> PostgreSQL
   +--> auction-service   :8081 -----> PostgreSQL / MongoDB / Redis / RabbitMQ
   +--> bid-service       :8082 ---------> PostgreSQL / Redis / RabbitMQ / MongoDB
   +--> analytics-service :8085 ---> PostgreSQL / MongoDB

RabbitMQ -> bid-service consumer -> Redis best bid -> Redis Pub/Sub -> notification-service :8083 -> WebSocket -> Frontend

Prometheus :9090 -> coleta métricas
Grafana    :3001 -> dashboards provisionados
```

Principais decisões:

- `PostgreSQL`: dados transacionais, como usuários, cargas, leilões e lances.
- `MongoDB`: auditoria imutável de eventos de negócio.
- `RabbitMQ`: fila para processar lances em ordem FIFO, evitando race conditions.
- `Redis`: estado do melhor lance atual e Pub/Sub de eventos.
- `Script Lua atômico`: GET + compare + SET em operação indivisível, eliminando race condition de lances concorrentes.
- `WebSocket`: notificações em tempo real para clientes conectados.
- `Prometheus` e `Grafana`: métricas, saúde e visualização operacional.
- `Docker Compose`: ambiente local reproduzível com um único comando.

## Serviços

| Serviço | Tecnologia | Responsabilidade |
| --- | --- | --- |
| `frontend` | React/TanStack Start | Interface web para ADMIN e TRANSPORTADORA |
| `api-gateway` | Java 21/Spring Boot | Entrada única HTTP, roteamento, CORS e JWT |
| `auth-service` | Java 21/Spring Boot | Login, cadastro, perfil e emissão de JWT |
| `auction-service` | Java 21/Spring Boot | Cargas, leilões, fechamento e vencedor |
| `bid-service` | Java 21/Spring Boot | Recebimento, validação e processamento atômico de lances |
| `notification-service` | Node.js | WebSocket, Redis Pub/Sub, notificações e métricas |
| `analytics-service` | Python/FastAPI | Agregações de leilões, lances e transportadoras |
| `postgres` | PostgreSQL | Banco relacional principal |
| `mongo` | MongoDB | Auditoria de eventos |
| `rabbitmq` | RabbitMQ | Mensageria de lances |
| `redis` | Redis | Cache e Pub/Sub |
| `prometheus` | Prometheus | Coleta de métricas |
| `grafana` | Grafana | Dashboards |
| `k6` | Grafana k6 | Testes de carga via profile |

## Como rodar

Requisitos:

- Docker
- Docker Compose

Não é necessário instalar Java, Node.js, Python ou Maven localmente. Tudo é executado dentro dos containers.

Subir todo o ambiente:

```bash
docker compose up --build
```

Subir em segundo plano:

```bash
docker compose up -d --build
```

Aguarde até todos os serviços estarem healthy. O startup leva cerca de 60–90 segundos na primeira execução.

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

### Criar usuários iniciais

Após subir o ambiente, crie um usuário ADMIN e uma TRANSPORTADORA para acessar o sistema:

```bash
# Criar ADMIN
curl -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Admin","email":"admin@freight.com","password":"senha123","role":"ADMIN"}'

# Criar TRANSPORTADORA
curl -X POST http://localhost:8080/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Transportadora","email":"transportadora@freight.com","password":"senha123","role":"TRANSPORTADORA"}'
```

Em seguida, acesse `http://localhost:5173` e faça login com um dos usuários criados.

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

## Interface gráfica

O frontend (React + TanStack Start) oferece telas distintas para os dois perfis de usuário.

**Perfil ADMIN:**

- Painel administrativo com métricas (leilões ativos, encerrados, lances do dia, transportadoras ativas)
- Criação de cargas e abertura de leilões
- Gerenciamento de transportadoras
- Fechamento manual de leilões
- Acesso a todo o histórico de leilões

**Perfil TRANSPORTADORA:**

- Dashboard com leilões abertos e histórico de participações
- Sala de leilão em tempo real: ranking de lances, contador regressivo e formulário de lance
- Alerta visual ao ser superado no lance
- Taxa de vitórias e volume mensal

**Funcionalidades comuns:**

- Notificações em tempo real via WebSocket (sino no topo direito + pop-ups)
- Feedback visual do status da conexão WebSocket
- Tratamento de erros com mensagens específicas do servidor
- Configurações de perfil (alterar nome e preferências de notificação)

## Fluxo principal

O fluxo de lance implementado é:

```text
POST /v1/bids
  -> API Gateway valida JWT
  -> bid-service valida leilão e transportadora
  -> bid-service persiste lance recebido no PostgreSQL
  -> RabbitMQ ordena processamento
  -> consumer compara lance com melhor valor no Redis usando Lua atômico
  -> bid-service marca lance como VALIDATED ou REJECTED
  -> Redis Pub/Sub publica evento
  -> notification-service envia WebSocket
  -> frontend atualiza ranking, pop-up e sino
```

Estado central em memória:

```text
auction:{auctionId}:best_bid
```

Formato do valor no Redis:

```text
amount|bidId|carrierId|receivedAt
```

Regra do leilão reverso:

- vence o menor lance;
- empate mantém o primeiro lance processado;
- lance igual ou maior que o menor lance atual é rejeitado;
- leilão fechado não aceita novos lances.

## Controle de concorrência

O mecanismo central de trava está no script Lua `compare_and_set_best_bid.lua`, carregado no Redis pelo `bid-service`. O Redis executa o script inteiro de forma single-threaded: nenhum outro comando roda no meio, tornando o ciclo "ler → comparar → gravar" indivisível do ponto de vista de qualquer outro cliente.

```lua
local currentBestBid = redis.call('GET', KEYS[1])
if currentBestBid == false then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
end
local separatorIndex = string.find(currentBestBid, '|')
local currentAmount  = tonumber(string.sub(currentBestBid, 1, separatorIndex - 1))
local newAmount      = tonumber(ARGV[1])
if newAmount < currentAmount then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
else
    return 0
end
```

Além do script Lua, a fila RabbitMQ garante que lances concorrentes sejam processados em ordem FIFO, e o Spring/Lettuce usa EVALSHA para reutilizar o script já carregado no Redis, evitando reenvio do texto a cada chamada.

## Autenticação e perfis

Perfis:

- `ADMIN`: cria cargas, cria leilões, acompanha todos os leilões, fecha leilões e recebe todas as notificações.
- `TRANSPORTADORA`: visualiza leilões, envia lances e recebe notificações dos leilões em que participa.

Criar usuário:

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

- nome pode ser alterado em configurações;
- email aparece no perfil como campo não editável;
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

Criar leilão:

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

Fechar leilão:

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

## Notificações em tempo real

WebSocket:

```text
ws://localhost:8083?auction=<auction-id>
```

Também há notificações globais para clientes conectados sem filtro de leilão:

```text
ws://localhost:8083
```

Eventos:

- `auction.opened`: novo leilão criado.
- `bid.validated`: novo menor lance aceito.
- `auction.closing`: aviso de leilão encerrando.
- `auction.closed`: leilão encerrado e vencedor definido.

Regras de visibilidade:

- ADMIN recebe todas as notificações.
- TRANSPORTADORA recebe notificações globais e notificações dos leilões em que participa.
- Uma transportadora não recebe notificação de lance de leilão em que não participa.
- Pop-ups aparecem em qualquer tela.
- Notificações ficam armazenadas no sino do topo direito.
- Pop-ups podem ser fechados manualmente.

## Persistência de dados

Os dados são duráveis entre reinicializações do ambiente através dos volumes Docker declarados no `docker-compose.yml`:

| Armazenamento | Volume Docker | Dados persistidos |
| --- | --- | --- |
| PostgreSQL | `leilao_data` | Usuários, cargas, leilões, lances |
| MongoDB | `mongo_data` | Auditoria de eventos de negócio |
| Grafana | `grafana_data` | Configurações e dashboards customizados |
| Redis | *(em memória)* | Estado do melhor lance — regenerado a cada sessão |

Use `docker compose down -v` apenas para zerar completamente o ambiente.

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

Endpoints úteis no host:

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

Métricas cobertas:

- saúde dos serviços;
- requisições HTTP;
- latência;
- erros;
- métricas JVM;
- métricas de lances;
- eventos e conexões WebSocket do `notification-service`;
- rotas do `api-gateway`;
- agregações do `analytics-service`.

## Testes

Testes unitários e de integração por serviço:

```bash
cd services/auction-service   && mvn test
cd services/bid-service       && mvn test
cd services/auth-service      && mvn test
cd services/api-gateway       && mvn test
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

- fluxo de autenticação com JWT;
- criação de carga e leilão;
- envio de lances válidos e inválidos;
- concorrência de lances;
- fechamento de leilão com vencedor;
- rejeição de lances após fechamento;
- persistência em PostgreSQL;
- auditoria em MongoDB;
- cache de melhor lance em Redis;
- fila RabbitMQ;
- Redis Pub/Sub e WebSocket;
- dashboards Grafana e targets Prometheus;
- frontend com login, dashboard, sala de leilão, histórico, admin e configurações.

## Estrutura do repositório

```text
.
├── docker-compose.yml
├── README.md
├── INTEGRACAO_FRONTEND.md
├── docs
├── frontend
│   └── bidflow-arena-main
│       └── src
│           ├── routes        ← login, dashboard, auction.$id, admin, settings
│           ├── hooks         ← useAuctions, useCarriers, useWebSocket
│           └── lib           ← store (Zustand), api, mock-data
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
│   │   └── src/main/resources/scripts/compare_and_set_best_bid.lua
│   └── notification-service
└── tests
    ├── e2e
    └── k6
```

## Documentação complementar

- `INTEGRACAO_FRONTEND.md`: detalhes da integração do frontend.
- `MERGE_PR_COMMANDS.md`: comandos históricos de merge usados durante a entrega.
- `docs/run-locally.md`: guia de execução local.
- `docs/test-dependencies.md`: dependências de testes.
- `docs/git-workflow.md`: fluxo de Git.
- `docs/entrega-2.md`: resumo da entrega anterior.

## Status da entrega

Implementado:

- arquitetura distribuída com microsserviços;
- API Gateway;
- autenticação JWT;
- leilão reverso de fretes;
- processamento assíncrono de lances;
- controle atômico do menor lance via Redis Lua;
- notificações em tempo real;
- frontend funcional para ADMIN e TRANSPORTADORA;
- dashboard administrativo e dashboard de transportadora;
- histórico de leilão;
- configurações de perfil;
- auditoria em MongoDB;
- analytics em FastAPI;
- observabilidade com Prometheus e Grafana;
- testes automatizados e scripts de carga.

Observação sobre gRPC:

O enunciado sugeria gRPC ou equivalente. Esta implementação usa HTTP/JSON via API Gateway, RabbitMQ, Redis e WebSocket como composição equivalente para procedimentos remotos, concorrência, ordenação, estado compartilhado e broadcast em tempo real.
