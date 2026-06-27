const Redis = require('ioredis');

function createRedisSubscriber() {
    const subscriber = new Redis({
        host: process.env.REDIS_HOST || 'localhost',
        port: process.env.REDIS_PORT || 6379
    });

    subscriber.on('connect', () => {
        console.log('RedisSubscriber conectado');
    });

    subscriber.on('error', (err) => {
        console.error('Erro no RedisSubscriber:', err);
    });

    return subscriber;
}

module.exports = {createRedisSubscriber};