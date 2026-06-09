# Plataforma de Negociacao de Fretes

Projeto 09 da disciplina de Sistemas Distribuidos.

## Objetivo

Construir uma plataforma de leilao reverso distribuido para fretes, onde transportadoras competem por cargas enviando lances progressivamente menores.

## Estrutura inicial

- `services/api-gateway`: gateway HTTP da plataforma.
- `services/auction-service`: servico responsavel por abrir e fechar leiloes.
- `services/bid-service`: servico responsavel por receber e publicar lances.
- `services/notification-service`: servico WebSocket para notificacoes em tempo real.
- `services/analytics-service`: servico de estatisticas.
- `services/auth-service`: servico de autenticacao e autorizacao.
- `frontend`: aplicacao web.
- `infra`: arquivos de infraestrutura, banco, filas e cache.
- `docs`: documentacao do projeto.
- `tests/k6`: testes de carga.
- `scripts`: scripts auxiliares.

## Sequencia atual

Seq 1: estrutura do repositorio e `.gitignore`.

## Fluxo Git

O fluxo de branches e commits esta documentado em `docs/git-workflow.md`.

## Rodar Localmente

O passo a passo para subir a infraestrutura e rodar os servicos esta em `docs/run-locally.md`.
