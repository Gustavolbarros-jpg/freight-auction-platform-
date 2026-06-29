# Plano de Integração Frontend ↔ Backend

## Visão Geral

O frontend (`bidflow-arena-main`) roda com **dados mockados** no Zustand.
O objetivo é substituir esses mocks por chamadas reais à API.

Tudo passa pelo **API Gateway na porta 8080**, exceto o WebSocket (porta 8083 direto).

---

## Prioridades

### PRIORIDADE 1 — Cliente de API e Auth (fazer primeiro, o resto depende disso)

**O que é:**
Criar um cliente HTTP com o token JWT no header automaticamente, e conectar o login real.

**Por que primeiro:**
Sem o token JWT, todas as outras chamadas são barradas pelo gateway.

**O que fazer:**

1. Criar `src/lib/api.ts` com uma função `apiFetch` que:
   - Bate em `http://localhost:8080` (API Gateway)
   - Pega o token do Zustand store e coloca no header `Authorization: Bearer <token>`
   - Lança erro se a resposta não for 2xx

2. No `store.ts`, a função `login()` hoje é falsa. Substituir por:
   ```
   POST http://localhost:8080/v1/auth/login
   Body: { email: string, password: string }
   Resposta: { token, tokenType, expiresAt, userId, role }
   ```
   Salvar o `token` e o `userId` no store.

3. A função `logout()` já funciona, só limpar token e user como faz hoje.

**Arquivo a criar:** `src/lib/api.ts`
**Arquivo a editar:** `src/lib/store.ts` — função `login()`

---

### PRIORIDADE 2 — Listar e buscar leilões reais

**O que é:**
Substituir os `seedAuctions()` do mock por dados reais do backend.

**Por que segundo:**
O dashboard e a sala de leilão dependem disso.

**O que fazer:**

1. Criar hook `src/hooks/useAuctions.ts` usando TanStack Query:
   ```
   GET http://localhost:8080/v1/auctions
   Header: Authorization: Bearer <token>
   ```

2. A resposta do backend tem esse formato:
   ```json
   {
     "id": "uuid",
     "loadId": "uuid",
     "status": "OPEN" | "CLOSED",
     "initialPrice": 14500.00,
     "startedAt": "2026-06-27T10:00:00",
     "closedAt": null,
     "createdByUserId": "uuid",
     "winnerCarrierId": null,
     "winningAmount": null
   }
   ```

3. Mapear para o tipo `Auction` do frontend:
   - `status: "OPEN"` → `"ABERTO"`, `"CLOSED"` → `"ENCERRADO"`
   - `initialPrice` → `initialValue` e `bestBid` (inicialmente iguais, WebSocket atualiza)
   - `loadId` → buscar `GET /v1/loads/{loadId}` para pegar os detalhes do cargo
   - `endsAt` → **não existe no backend ainda** (ver Prioridade 5)

4. Para buscar um leilão específico:
   ```
   GET http://localhost:8080/v1/auctions/{id}
   ```

**Arquivo a criar:** `src/hooks/useAuctions.ts`
**Arquivo a editar:** `src/routes/dashboard.tsx`, `src/routes/auction.$id.tsx`

---

### PRIORIDADE 3 — WebSocket com dados reais

**O que é:**
O hook `useAuctionWebSocket` já tenta conectar em `ws://localhost:8083?auction={id}`.
Falta parsear os eventos que chegam do backend.

**Por que terceiro:**
Com login e listagem funcionando, a sala de leilão precisa de dados em tempo real.

**O que fazer:**

1. O backend publica eventos no canal `bid.validated` com esse formato:
   ```json
   {
     "auctionId": "uuid",
     "bidId": "uuid",
     "carrierId": "uuid",
     "amount": 12500.00,
     "receivedAt": "2026-06-27T10:05:00Z"
   }
   ```

2. No `use-websocket.ts`, no `ws.onmessage`, parsear essa mensagem e chamar `addBidToAuction()`.

3. O backend também publica `auction.closed`:
   ```json
   {
     "auctionId": "uuid",
     "status": "CLOSED",
     "closedAt": "...",
     "winnerCarrierId": "uuid",
     "winningAmount": 11200.00
   }
   ```
   Ao receber, chamar `closeAuction()` no store.

4. E `auction.opened` (leilão novo):
   ```json
   {
     "auctionId": "uuid",
     "loadId": "uuid",
     "status": "OPEN",
     "startedAt": "..."
   }
   ```

**Arquivo a editar:** `src/lib/use-websocket.ts`

---

### PRIORIDADE 4 — Fazer lance e fechar leilão

**O que é:**
Substituir as ações locais do store por chamadas reais.

**Por que quarto:**
Depende do login (P1) e da listagem (P2) funcionando.

**Fazer lance:**
```
POST http://localhost:8080/v1/bids
Header: Authorization: Bearer <token>
Body: { auctionId: "uuid", amount: 12500.00 }
```
O backend retorna `202 Accepted`. O resultado real chega via WebSocket.

**Fechar leilão (só ADMIN):**
```
PATCH http://localhost:8080/v1/auctions/{id}/close
Header: Authorization: Bearer <token>
```

**Arquivo a editar:** `src/lib/store.ts` — funções `addBidToAuction()` e `closeAuction()`

---

### PRIORIDADE 5 — Corrigir gaps no backend (mudanças no Java)

**O que é:**
Ajustes pequenos no backend para fornecer dados que o frontend precisa mas ainda faltam.

**Gap 1 — `endsAt` não existe na `AuctionResponse`**

O frontend usa `endsAt` (epoch ms) para o contador regressivo.
O backend não persiste isso. Solução: adicionar `durationMinutes` na entidade `Auction`
e incluí-lo na `AuctionResponse`.

Arquivo a editar no backend:
- `Auction.java` (domain) — adicionar campo `durationMinutes`
- `AuctionResponse.java` — adicionar `durationMinutes`
- Criar migração Flyway `V7__add_duration_minutes_to_auctions.sql`

O frontend calcula: `endsAt = new Date(startedAt).getTime() + durationMinutes * 60000`

**Gap 2 — Não existe endpoint de listagem de bids por leilão**

O frontend mostra o histórico de lances na sala. O backend só tem `/best`.
Solução: adicionar `GET /v1/bids/auctions/{auctionId}` retornando todos os bids.

Arquivo a editar no backend:
- `BidController.java` — novo endpoint
- `BidRepository.java` — `findByAuctionId(UUID auctionId)`

**Gap 3 — `leader` é UUID, frontend quer nome**

O frontend mostra o nome da transportadora vencedora.
O backend só tem `winnerCarrierId` (UUID). 

Soluções possíveis:
- Opção A: frontend usa o UUID e exibe abreviado ("TRP-xxxx")
- Opção B: adicionar endpoint no auth-service para buscar nome por ID
- Opção C: bid-service retornar o nome junto (mais acoplamento)

Recomendação: **Opção A por enquanto** — exibir o UUID curto até ter um endpoint de usuário.

---

### PRIORIDADE 6 — Criar leilão pelo frontend (fluxo admin)

**O que é:**
A tela de admin cria leilões. No backend são **dois passos**: primeiro cria o Load, depois cria o Auction.

**Por que por último:**
É o fluxo mais complexo e depende de tudo anterior funcionando.

**O que fazer:**

Passo 1 — Criar o Load:
```
POST http://localhost:8080/v1/loads
Header: Authorization: Bearer <token>
Body: {
  origin: "São Paulo - SP",
  destination: "Recife - PE",
  description: "Carga de eletrodomésticos",
  weightKg: 12500,
  initialPrice: 14500.00
}
```
Resposta: `{ id: "uuid", ... }`

Passo 2 — Criar o Auction com o loadId retornado:
```
POST http://localhost:8080/v1/auctions
Header: Authorization: Bearer <token>
Body: {
  loadId: "uuid-do-load-criado",
  durationMinutes: 60
}
```

**Arquivo a editar:** `src/routes/admin.tsx` — formulário de criação de leilão

---

## Ordem recomendada de execução

```
P1 (api.ts + login real)
  └── P2 (listar leilões)
        └── P3 (WebSocket real)
              └── P4 (fazer lance + fechar)
                    └── P6 (criar leilão)

P5 (ajustes backend) — pode fazer em paralelo com P2/P3
```

---

## Referência rápida dos endpoints

| Ação | Método | URL |
|---|---|---|
| Login | POST | `http://localhost:8080/v1/auth/login` |
| Registrar | POST | `http://localhost:8080/v1/auth/register` |
| Listar leilões | GET | `http://localhost:8080/v1/auctions` |
| Buscar leilão | GET | `http://localhost:8080/v1/auctions/{id}` |
| Criar load | POST | `http://localhost:8080/v1/loads` |
| Criar leilão | POST | `http://localhost:8080/v1/auctions` |
| Fechar leilão | PATCH | `http://localhost:8080/v1/auctions/{id}/close` |
| Fazer lance | POST | `http://localhost:8080/v1/bids` |
| Melhor lance | GET | `http://localhost:8080/v1/bids/auctions/{id}/best` |
| WebSocket | WS | `ws://localhost:8083?auction={id}` |

Todas as rotas (exceto login/register e WebSocket) precisam do header:
```
Authorization: Bearer <token>
```
