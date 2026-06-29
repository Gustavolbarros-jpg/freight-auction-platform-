const http = require('http');
const { createWebSocketServer } = require('./controller/WebSocketController');
const { createRedisSubscriber } = require('./repository/RedisSubscriber');
const { handleBidEvent } = require('./service/NotificationService');
const { register } = require('./metrics');

const PORT = process.env.WS_PORT || 8083;

const server = http.createServer(async (req, res) => {
    if (req.url === '/metrics') {
        res.setHeader('Content-Type', register.contentType);
        res.end(await register.metrics());
        return;
    }
    if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'UP', service: 'notification-service' }));
        return;
    }
    res.writeHead(404);
    res.end();
});

const wss = createWebSocketServer(server);

const subscriber = createRedisSubscriber();

subscriber.subscribe('bid.validated', 'auction.closed', 'auction.opened', (err) => {
    if (err) {
        console.error('Erro ao assinar canais Redis:', err);
        process.exit(1);
    }
    console.log('Inscrito nos canais: bid.validated, auction.closed, auction.opened');
});

subscriber.on('message', (channel, message) => {
    handleBidEvent(wss, channel, message);
});

server.listen(PORT, () => {
    console.log(`Notification Service rodando na porta ${PORT}`);
});
