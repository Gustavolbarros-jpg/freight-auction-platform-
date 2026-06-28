-- compare_and_set_best_bid.lua
--
-- Faz GET + compare + SET em uma única operação atômica no Redis,
-- eliminando a race condition que existia no BestBidService.process()
-- (antes: GET e SET eram duas chamadas separadas, então dois bids
-- concorrentes podiam ler o mesmo "melhor lance atual" e ambos decidirem
-- que eram menores, sobrescrevendo um ao outro).
--
-- O Redis executa o script inteiro de forma single-threaded: nenhum outro
-- comando roda no meio, então o "ler valor atual -> comparar -> gravar"
-- vira indivisível do ponto de vista de qualquer outro cliente.
--
-- KEYS[1] = chave do melhor lance (ex.: "auction:<id>:best_bid")
-- ARGV[1] = valor do novo lance (amount), usado para a comparação numérica
-- ARGV[2] = valor serializado completo a ser gravado se este lance vencer
--
-- Retorno: 1 se este lance se tornou o melhor lance; 0 caso contrário.

local currentBestBid = redis.call('GET', KEYS[1])

if currentBestBid == false then
    -- Não existe lance ainda para este auction: este é automaticamente o melhor.
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
end

-- O valor armazenado é "amount|bidId|carrierId|receivedAt" (ver BestBidService.serialize).
-- Extrai só o campo amount (antes do primeiro "|") pra comparar.
local separatorIndex = string.find(currentBestBid, '|')
local currentAmount = tonumber(string.sub(currentBestBid, 1, separatorIndex - 1))
local newAmount = tonumber(ARGV[1])

if newAmount < currentAmount then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
else
    return 0
end