const { WebSocketServer, WebSocket } = require('ws');

function createWebSocketServer(port) {
  const wss = new WebSocketServer({ port });

  wss.on('connection', (ws, req) => {
    const params = new URLSearchParams(req.url.replace('/?', ''));
    ws.auctionId = params.get('auction');

    console.log(`Cliente conectado no leilao: ${ws.auctionId}`);

    ws.on('close', () => {
      console.log(`Cliente desconectado do leilao: ${ws.auctionId}`);
    });
  });

  return wss;
}

function broadcast(wss, auctionId, payload) {
  wss.clients.forEach((client) => {
    if (client.auctionId === auctionId && client.readyState === WebSocket.OPEN) {
      client.send(payload);
    }
  });
}

module.exports = { createWebSocketServer, broadcast };
