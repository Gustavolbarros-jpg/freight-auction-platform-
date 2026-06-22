# Entrega 2 - Resumo Final

Este documento resume o que foi implementado na Entrega 2 da Plataforma de Negociacao de Fretes.

O objetivo desta entrega foi sair de um fluxo distribuido basico e chegar a um fluxo de negocio mais completo, com autenticacao, validacao entre servicos, persistencia, notificacoes em tempo real e fechamento de leilao com vencedor.

## Escopo Entregue

### 1. Logs estruturados

Foram adicionados logs nos pontos principais dos servicos Java:

- recebimento de requisicoes nos controllers;
- criacao e fechamento de leiloes;
- recebimento, validacao e processamento de lances;
- recusa de lances invalidos;
- atualizacao do melhor lance.

Servicos impactados:

- `auction-service`
- `bid-service`

### 2. Notificacoes em tempo real

O fluxo de notificacao foi conectado ao Redis Pub/Sub.

Eventos publicados:

- `bid.validated`: publicado quando um lance se torna o novo melhor lance;
- `auction.closed`: publicado quando um leilao e fechado.

O `notification-service` recebe esses eventos e envia para clientes conectados por WebSocket:

```text
ws://localhost:8083/?auction=<auction-id>
```

### 3. Auth service com JWT

Foi criado o `auth-service`, responsavel por cadastro, login e emissao de tokens JWT.

Endpoints principais:

```http
POST /v1/auth/register
POST /v1/auth/login
GET /v1/auth/validate
```

Regras aplicadas:

- usuarios possuem papel `ADMIN` ou `TRANSPORTADORA`;
- endpoints de escrita de cargas/leiloes exigem `ADMIN`;
- endpoint de criacao de lance exige `TRANSPORTADORA`;
- o `carrierId` do lance nao vem mais do corpo da requisicao, ele e extraido do token.

### 4. Validacao de leilao antes do lance

O `bid-service` passou a consultar o `auction-service` antes de aceitar um lance.

Regras:

- se o leilao nao existir, o lance e recusado;
- se o leilao nao estiver `OPEN`, o lance e recusado;
- lances recusados nao sao publicados no RabbitMQ.

### 5. Persistencia dos lances

Os lances passaram a ser persistidos no PostgreSQL pelo `bid-service`.

Foi criada a tabela `bids` com status de processamento.

Status usados:

- `RECEIVED`: lance recebido e salvo antes de ir para a fila;
- `VALIDATED`: lance processado e aceito como melhor lance;
- `REJECTED`: lance processado, mas nao substituiu o melhor lance;
- `WINNING`: reservado para evolucao do fluxo de vencedor.

O Redis continua sendo usado como leitura rapida do melhor lance atual.

### 6. Fechamento de leilao com vencedor

O fechamento de leilao agora busca o melhor lance no `bid-service` e grava o vencedor no `auction-service`.

Ao chamar:

```http
PATCH /v1/auctions/{id}/close
```

o sistema:

1. consulta o melhor lance do leilao;
2. grava `winnerCarrierId`;
3. grava `winningAmount`;
4. muda o status para `CLOSED`;
5. publica `auction.closed`;
6. impede novos lances para o leilao fechado.

### 7. Testes de dominio

Foram adicionados testes unitarios para as regras principais:

- aceitar lance apenas para leilao aberto;
- recusar lance para leilao fechado;
- recusar lance para leilao inexistente;
- salvar e publicar lance valido;
- manter o menor lance como melhor lance;
- manter o primeiro lance em caso de empate;
- fechar leilao aberto com vencedor;
- recusar fechamento de leilao ja fechado.

Servicos cobertos:

- `bid-service`
- `auction-service`
- `auth-service` com testes de JWT.

### 8. Documentacao

O `README.md` foi atualizado com:

- arquitetura atualizada;
- fluxo completo de lances;
- autenticacao;
- exemplos de `curl`;
- portas dos servicos;
- validacao feita;
- proximos passos.

Tambem foi criado o plano detalhado de execucao:

```text
docs/entrega-2-plano-execucao.md
```

## Fluxo Final

```text
admin cria carga
admin cria leilao
transportadora faz login
transportadora envia lance
bid-service valida JWT
bid-service consulta auction-service
bid-service salva lance no Postgres
bid-service publica evento no RabbitMQ
consumer processa a fila
Redis guarda o melhor lance
notification-service envia evento por WebSocket
admin fecha leilao
auction-service grava vencedor
novos lances passam a ser recusados
```

## Como Rodar

Subir todos os servicos:

```bash
docker compose up --build
```

Conferir os containers:

```bash
docker compose ps
```

Servicos principais:

- `auction-service`: `http://localhost:8081`
- `bid-service`: `http://localhost:8082`
- `notification-service`: `http://localhost:8083`
- `auth-service`: `http://localhost:8084`
- RabbitMQ Management: `http://localhost:15672`

## Resultado

A Entrega 2 deixa o projeto com um fluxo distribuido completo para o dominio principal:

```text
criar carga -> criar leilao -> autenticar usuario -> receber lance -> validar leilao -> processar fila -> persistir status -> atualizar melhor lance -> notificar em tempo real -> fechar leilao -> registrar vencedor
```

Esse fluxo usa comunicacao HTTP entre servicos, mensageria com RabbitMQ, cache/eventos com Redis, persistencia com PostgreSQL e notificacoes em tempo real com WebSocket.
