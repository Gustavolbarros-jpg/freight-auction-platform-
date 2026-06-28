const {broadcast, broadcastAll} = require('../controller/WebSocketController');
const {buildPayload} = require('../dto/NotificationPayload');

// Canais que devem ir só pros clientes do leilão específico (filtrados por auctionId)
const SCOPED_CHANNELS = new Set(['bid.validated', 'auction.closed']);

function handleBidEvent(wss, channel, message) {
    try {
        const event = JSON.parse(message);
        const payload = buildPayload(channel, event.auctionId, event);

        if (SCOPED_CHANNELS.has(channel)) {
            broadcast(wss, event.auctionId, payload);
        } else {
            // auction.opened (e qualquer outro canal global futuro): avisa todo mundo conectado
            broadcastAll(wss, payload);
        }

        console.log(`Evento [${channel}] transmitido para leilao: ${event.auctionId}`);
    } catch (err) {
        console.error('Erro ao processar evento:', err);
    }
}

module.exports = { handleBidEvent };