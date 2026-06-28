#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE_URL:-http://localhost}"
GREEN='\033[0;32m'; RED='\033[0;31m'; RESET='\033[0m'

pass() { echo -e "${GREEN}[PASS]${RESET} $1"; }
fail() { echo -e "${RED}[FAIL]${RESET} $1"; exit 1; }

echo "=== E2E: Freight Auction Platform ==="
echo ""

echo "[1/7] Registrando usuarios..."
curl -sf -X POST "$BASE:8084/v1/auth/register" -H 'Content-Type: application/json' \
  -d '{"name":"Admin E2E","email":"admin-e2e@test.com","password":"senha123","role":"ADMIN"}' > /dev/null || true
curl -sf -X POST "$BASE:8084/v1/auth/register" -H 'Content-Type: application/json' \
  -d '{"name":"Carrier E2E","email":"carrier-e2e@test.com","password":"senha123","role":"TRANSPORTADORA"}' > /dev/null || true
pass "Usuarios registrados"

echo "[2/7] Login como ADMIN..."
ADMIN_TOKEN=$(curl -sf -X POST "$BASE:8084/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin-e2e@test.com","password":"senha123"}' | jq -r '.token')
[ -n "$ADMIN_TOKEN" ] && pass "Token ADMIN obtido" || fail "Login ADMIN falhou"

echo "[3/7] Login como TRANSPORTADORA..."
CARRIER_TOKEN=$(curl -sf -X POST "$BASE:8084/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"carrier-e2e@test.com","password":"senha123"}' | jq -r '.token')
[ -n "$CARRIER_TOKEN" ] && pass "Token CARRIER obtido" || fail "Login CARRIER falhou"

echo "[4/7] Criando carga..."
LOAD_ID=$(curl -sf -X POST "$BASE:8081/v1/loads" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"origin":"Recife","destination":"Olinda","description":"Carga E2E","weightKg":100,"initialPrice":1000}' | jq -r '.id')
[ -n "$LOAD_ID" ] && pass "Carga criada: $LOAD_ID" || fail "Criacao de carga falhou"

echo "[5/7] Criando leilao..."
AUCTION_ID=$(curl -sf -X POST "$BASE:8081/v1/auctions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"loadId\":\"$LOAD_ID\",\"durationMinutes\":5}" | jq -r '.id')
[ -n "$AUCTION_ID" ] && pass "Leilao criado: $AUCTION_ID" || fail "Criacao de leilao falhou"

echo "[6/7] Enviando lances..."
curl -sf -X POST "$BASE:8082/v1/bids" \
  -H "Authorization: Bearer $CARRIER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":900.00}" > /dev/null
pass "Lance de 900.00 enviado"

curl -sf -X POST "$BASE:8082/v1/bids" \
  -H "Authorization: Bearer $CARRIER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":750.00}" > /dev/null
pass "Lance de 750.00 enviado"

curl -sf -X POST "$BASE:8082/v1/bids" \
  -H "Authorization: Bearer $CARRIER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":850.00}" > /dev/null
pass "Lance de 850.00 enviado (deve ser ignorado)"

echo "Aguardando processamento assincrono..."
sleep 30

BEST=$(curl -sf "$BASE:8082/v1/bids/auctions/$AUCTION_ID/best")
echo "Melhor lance: $BEST"
echo "$BEST" | grep -q "750" && pass "Melhor lance correto: 750.00" || fail "Melhor lance incorreto"

echo "[7/7] Fechando leilao..."
RESULT=$(curl -sf -X PATCH "$BASE:8081/v1/auctions/$AUCTION_ID/close" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "Resultado: $RESULT"
echo "$RESULT" | grep -q "winningAmount" && pass "Campo winningAmount presente" || fail "winningAmount ausente"
echo "$RESULT" | grep -qE '"winningAmount":[^n]' && pass "Valor vencedor presente" || fail "winningAmount null"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE:8082/v1/bids" \
  -H "Authorization: Bearer $CARRIER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"auctionId\":\"$AUCTION_ID\",\"amount\":100.00}")
{ [ "$STATUS" = "409" ] || [ "$STATUS" = "422" ]; } && pass "Lance em leilao fechado bloqueado ($STATUS)" || fail "Esperava 409 ou 422, recebi $STATUS"

echo ""
echo "=== Todos os passos E2E passaram! ==="
