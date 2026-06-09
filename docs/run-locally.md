# Como Rodar Localmente

Este guia mostra como subir a infraestrutura do projeto e rodar o `auction-service`.

## Requisitos

- Git
- Docker
- Docker Compose
- Java 21

## 1. Clonar o Projeto

```bash
git clone https://github.com/Gustavolbarros-jpg/freight-auction-platform-.git
cd freight-auction-platform-
```

## 2. Subir a Infraestrutura

Na raiz do projeto, rode:

```bash
docker compose up -d
```

Esse comando sobe:

- PostgreSQL na porta `5432`
- Redis na porta `6379`
- RabbitMQ na porta `5672`
- Painel do RabbitMQ na porta `15672`
- MongoDB na porta `27017`

Para conferir:

```bash
docker compose ps
```

## 3. Rodar o Auction Service

Entre na pasta do servico:

```bash
cd services/auction-service
```

Rode:

```bash
./mvnw spring-boot:run
```

O servico deve iniciar em:

```text
http://localhost:8081
```

## 4. Dados de Conexao do PostgreSQL

Use estes dados no DBeaver ou no servico:

```text
Host: localhost
Porta: 5432
Database: freight_auction
User: freight_user
Password: example
```

## 5. RabbitMQ Management

Painel:

```text
http://localhost:15672
```

Credenciais:

```text
User: user
Password: password
```

## Problema Comum: Porta 5432 Ocupada

Se aparecer erro parecido com:

```text
failed to bind host port 0.0.0.0:5432/tcp: address already in use
```

significa que ja existe outro PostgreSQL usando a porta `5432`.

Opcao 1: parar o PostgreSQL local:

```bash
sudo systemctl stop postgresql
docker compose up -d
```

Opcao 2: mudar a porta externa no `docker-compose.yml`:

```yaml
ports:
  - "5433:5432"
```

Se usar a porta `5433`, tambem altere no `application.properties` do `auction-service`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/freight_auction
```

## Parar o Projeto

Para parar os containers:

```bash
docker compose down
```

Para parar e apagar os volumes de dados:

```bash
docker compose down -v
```

Use `down -v` apenas quando quiser apagar os dados locais do banco.

