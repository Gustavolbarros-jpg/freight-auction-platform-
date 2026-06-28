const WebSocket = require('ws')
const { createWebSocketServer, broadcast } = require('../../src/controller/WebSocketController')

const TEST_PORT = 9099
const WS_URL = `ws://localhost:${TEST_PORT}`

let wss

beforeAll((done) => {
  wss = createWebSocketServer(TEST_PORT)
  wss.on('listening', done)
})

afterAll((done) => {
  wss.close(done)
})

describe('WebSocket integration', () => {

  test('cliente deve conectar e receber auctionId via parâmetro de URL', (done) => {
    const auctionId = '11111111-1111-1111-1111-111111111111'
    const client = new WebSocket(`${WS_URL}/?auction=${auctionId}`)

    client.on('open', () => {
      setTimeout(() => {
        let found = false
        wss.clients.forEach((c) => {
          if (c.auctionId === auctionId) found = true
        })
        expect(found).toBe(true)
        client.close()
        done()
      }, 100)
    })
  })

  test('broadcast deve enviar mensagem apenas para clientes do leilão correto', (done) => {
    const auctionId = '22222222-2222-2222-2222-222222222222'
    const outroAuctionId = '33333333-3333-3333-3333-333333333333'
    const client1 = new WebSocket(`${WS_URL}/?auction=${auctionId}`)
    const client2 = new WebSocket(`${WS_URL}/?auction=${outroAuctionId}`)
    const mensagensRecebidas = []

    client1.on('message', (msg) => mensagensRecebidas.push({ client: 1, msg: msg.toString() }))
    client2.on('message', (msg) => mensagensRecebidas.push({ client: 2, msg: msg.toString() }))

    let conectados = 0
    const onOpen = () => {
      conectados++
      if (conectados === 2) {
        setTimeout(() => {
          const payload = JSON.stringify({ type: 'bid.validated', auctionId, amount: 750 })
          broadcast(wss, auctionId, payload)
          setTimeout(() => {
            expect(mensagensRecebidas.length).toBe(1)
            expect(mensagensRecebidas[0].client).toBe(1)
            expect(JSON.parse(mensagensRecebidas[0].msg).auctionId).toBe(auctionId)
            client1.close()
            client2.close()
            done()
          }, 100)
        }, 100)
      }
    }

    client1.on('open', onOpen)
    client2.on('open', onOpen)
  })

  test('cliente desconectado não deve receber mensagem', (done) => {
    const auctionId = '44444444-4444-4444-4444-444444444444'
    const client = new WebSocket(`${WS_URL}/?auction=${auctionId}`)

    client.on('open', () => {
      client.close()
      setTimeout(() => {
        expect(() => {
          broadcast(wss, auctionId, JSON.stringify({ type: 'bid.validated', auctionId }))
        }).not.toThrow()
        done()
      }, 200)
    })
  })
})