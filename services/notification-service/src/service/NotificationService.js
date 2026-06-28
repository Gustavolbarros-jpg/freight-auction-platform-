const WebSocketController = require('../controller/WebSocketController');
const { buildPayload } = require('../dto/NotificationPayload');

function handleBidEvent(wss, channel, message) {
    try {
        const event = JSON.parse(message);
        const payload = buildPayload(channel, event.auctionId, event);

        WebSocketController.broadcast(wss, event.auctionId, payload);

        console.log(`Evento [${channel}] transmitido para leilao: ${event.auctionId}`);
    } catch (err) {
        console.error('Erro ao processar evento:', err);
    }
}

module.exports = { handleBidEvent };