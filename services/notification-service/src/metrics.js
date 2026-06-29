const client = require('prom-client');

const register = new client.Registry();
client.collectDefaultMetrics({ register });

const eventsReceived = new client.Counter({
    name: 'notification_events_received_total',
    help: 'Total de eventos recebidos do Redis por canal',
    labelNames: ['channel'],
    registers: [register],
});

const notificationsSent = new client.Counter({
    name: 'notification_sent_total',
    help: 'Total de notificacoes enviadas via WebSocket por canal',
    labelNames: ['channel'],
    registers: [register],
});

const activeConnections = new client.Gauge({
    name: 'notification_websocket_connections_active',
    help: 'Numero de conexoes WebSocket ativas',
    registers: [register],
});

module.exports = { register, eventsReceived, notificationsSent, activeConnections };
