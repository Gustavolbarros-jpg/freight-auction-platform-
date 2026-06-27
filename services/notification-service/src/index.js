const { createWebSocketServer } = require('./controller/WebSocketController');
const { createRedisSubscriber } = require('./repository/RedisSubscriber');
const { handleBidEvent } = require('./service/NotificationService');

const PORT = process.env.WS_PORT || 8083;

const wss = createWebSocketServer(PORT);

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

console.log(`Notification Service rodando na porta ${PORT}`);