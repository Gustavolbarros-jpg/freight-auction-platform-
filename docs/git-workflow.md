# Fluxo Git do Projeto

Este projeto usa um fluxo simples com `main`, `develop` e branches de feature.

## Branches

- `main`: versao estavel do projeto.
- `develop`: branch de integracao do desenvolvimento.
- `feature/*`: branches para tarefas novas.
- `fix/*`: branches para correcoes.

## Fluxo de Trabalho

1. Atualize a `develop`.
2. Crie uma branch a partir da `develop`.
3. Faca a tarefa.
4. Faca commits pequenos.
5. Abra pull request para `develop`.
6. Depois de revisar, faca merge na `develop`.
7. Quando uma entrega estiver estavel, faca merge de `develop` para `main`.

## Exemplos de Branch

```bash
git checkout develop
git pull origin develop
git checkout -b feature/auction-service-loads
```

## Padrao de Commit

Use o formato:

```text
tipo(escopo): descricao curta
```

Tipos principais:

- `feat`: nova funcionalidade.
- `fix`: correcao de bug.
- `docs`: documentacao.
- `chore`: configuracao, scripts ou ajustes sem regra de negocio.
- `test`: testes.
- `refactor`: melhoria interna sem mudar comportamento.

Exemplos:

```text
feat(auction-service): create load endpoint
chore(infra): add postgres migrations
docs(git): add workflow guide
fix(auction-service): correct load mapper response order
```

## Regra Importante

Nao trabalhe direto na `main`.

Use:

```text
feature/* -> develop -> main
```
