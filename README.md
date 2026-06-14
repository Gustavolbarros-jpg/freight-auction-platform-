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
- `notification-service`: servico Node.js preparado para notificacoes via WebSocket e Redis Pub/Sub.
- `postgres`: banco relacional usado pelos servicos que precisam persistir dados.
- `rabbitmq`: broker de mensagens usado para processar lances em fila.
- `redis`: cache usado para guardar o melhor lance atual por leilao.

Tambem existem arquivos de infraestrutura e documentacao em:

- `docker-compose.yml`
- `infra/postgres/migrations`
- `docs`

## Decisoes Tecnologicas

A arquitetura foi pensada como uma plataforma distribuida baseada em microsservicos, com comunicacao HTTP para operacoes sincronas e mensageria para operacoes assincronas.

Tecnologias escolhidas:

- Java 21 com Spring Boot para `auction-service` e `bid-service`: escolha feita por oferecer suporte maduro para APIs REST, validacao de entrada, configuracao por ambiente, integracao com banco relacional, RabbitMQ e Redis.
- Node.js para `notification-service`: escolha feita por se encaixar bem no modelo de conexoes WebSocket e notificacoes em tempo real.
- PostgreSQL para dados persistentes: usado para entidades que precisam sobreviver ao ciclo de vida dos servicos, como cargas, leiloes e futuramente historico de lances.
- RabbitMQ para fila de lances: usado para desacoplar o recebimento HTTP do processamento dos lances e preservar a ordem de chegada no consumidor.
- Redis para estado rapido em memoria: usado para consultar rapidamente o melhor lance atual de cada leilao.
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
POST /bids -> RabbitMQ -> consumer FIFO -> Redis -> GET best bid
```

Na pratica:

1. O cliente envia um lance para `POST /bids`.
2. O `bid-service` valida a requisicao e publica o evento no RabbitMQ.
3. O consumidor do `bid-service` le a fila com apenas um consumidor ativo.
4. Esse processamento sequencial preserva a ordem dos lances na fila.
5. O servico compara o novo lance com o melhor lance salvo no Redis.
6. Se o novo lance for menor, ele passa a ser o melhor lance.
7. Se o lance for igual ou maior, o melhor lance atual continua.

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

O projeto define dois tipos principais de comunicacao:

- HTTP/JSON para chamadas externas e consultas.
- RabbitMQ para eventos assincronos de lances.

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

## Endpoints Principais

### Criar lance

```http
POST /bids
```

Exemplo:

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

O status `202` indica que o lance foi aceito para processamento assincrono na fila.

### Consultar melhor lance

```http
GET /bids/auctions/{auctionId}/best
```

Retorna o melhor lance salvo no Redis para o leilao informado.

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
- `postgres`: `5432`
- `rabbitmq`: `5672`
- `rabbitmq-management`: `15672`
- `redis`: `6379`

## Validacao Feita

O fluxo do `bid-service` foi validado com Docker:

- Redis e RabbitMQ subiram saudaveis.
- `bid-service` subiu na porta `8082`.
- `POST /bids` retornou `202 Accepted`.
- A mensagem foi processada pelo consumer via RabbitMQ.
- O melhor lance foi salvo no Redis.
- `GET /bids/auctions/{auctionId}/best` retornou o menor lance.
- Em caso de empate, o primeiro lance processado foi mantido.

## Documentacao

- `docs/git-workflow.md`: fluxo de branches e commits.
- `docs/run-locally.md`: orientacoes para rodar localmente.
- `docs/bid-service-implementation.md`: detalhes da implementacao do `bid-service`.
- `docs/bid-service-docker-handoff.md`: pendencias e orientacoes para completar/validar Docker.

## Proximos Passos

Itens que podem entrar nas proximas entregas:

- validar no `bid-service` se o leilao existe antes de aceitar lance;
- persistir historico de lances em banco;
- integrar o melhor lance com o `notification-service`;
- adicionar testes unitarios e testes de integracao com RabbitMQ/Redis;
- evoluir gateway, autenticacao, frontend e analytics.
