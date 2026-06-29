const {broadcast, broadcastAll} = require('../controller/WebSocketController');
const {buildPayload} = require('../dto/NotificationPayload');
const { eventsReceived, notificationsSent } = require('../metrics');

const SCOPED_CHANNELS = new Set(['bid.validated', 'auction.closed']);

function handleBidEvent(wss, channel, message) {
    try {
        const event = JSON.parse(message);
        const payload = buildPayload(channel, event.auctionId, event);

        eventsReceived.inc({ channel });

        if (SCOPED_CHANNELS.has(channel)) {
            broadcast(wss, event.auctionId, payload);
        } else {
            broadcastAll(wss, payload);
        }

        notificationsSent.inc({ channel });
        console.log(`Evento [${channel}] transmitido para leilao: ${event.auctionId}`);
    } catch (err) {
        console.error('Erro ao processar evento:', err);
    }
}

module.exports = { handleBidEvent };