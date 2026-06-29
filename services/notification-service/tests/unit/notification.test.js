const { buildPayload } = require('../../src/dto/NotificationPayload')

describe('NotificationService', () => {

  let handleBidEvent
  let mockBroadcast

  beforeEach(() => {
    jest.resetModules()

    mockBroadcast = jest.fn()

    jest.mock('../../src/controller/WebSocketController', () => ({
      broadcast: mockBroadcast
    }))

    handleBidEvent = require('../../src/service/NotificationService').handleBidEvent
  })

  test('deve chamar broadcast com payload correto para bid.validated', () => {
    const wss = {}
    const auctionId = '11111111-1111-1111-1111-111111111111'
    const event = { auctionId, carrierId: 'abc', amount: 750.00 }

    handleBidEvent(wss, 'bid.validated', JSON.stringify(event))

    expect(mockBroadcast).toHaveBeenCalledTimes(1)
    expect(mockBroadcast).toHaveBeenCalledWith(wss, auctionId, expect.any(String))

    const sentPayload = JSON.parse(mockBroadcast.mock.calls[0][2])
    expect(sentPayload.type).toBe('bid.validated')
    expect(sentPayload.auctionId).toBe(auctionId)
    expect(sentPayload.data).toEqual(event)
    expect(sentPayload.sentAt).toBeDefined()
  })

  test('deve chamar broadcast com payload correto para auction.closed', () => {
    const wss = {}
    const auctionId = '22222222-2222-2222-2222-222222222222'
    const event = { auctionId, winnerCarrierId: 'xyz', winningAmount: 600.00 }

    handleBidEvent(wss, 'auction.closed', JSON.stringify(event))

    expect(mockBroadcast).toHaveBeenCalledTimes(1)
    const sentPayload = JSON.parse(mockBroadcast.mock.calls[0][2])
    expect(sentPayload.type).toBe('auction.closed')
    expect(sentPayload.auctionId).toBe(auctionId)
  })

  test('não deve lançar exceção quando a mensagem é JSON inválido', () => {
    const wss = {}

    expect(() => handleBidEvent(wss, 'bid.validated', 'invalid-json')).not.toThrow()
    expect(mockBroadcast).not.toHaveBeenCalled()
  })

  test('não deve lançar exceção quando broadcast lança erro', () => {
    mockBroadcast.mockImplementation(() => { throw new Error('ws error') })
    const wss = {}
    const event = { auctionId: '33333333-3333-3333-3333-333333333333' }

    expect(() => handleBidEvent(wss, 'bid.validated', JSON.stringify(event))).not.toThrow()
  })

  test('buildPayload deve serializar tipo, auctionId e data corretamente', () => {
    const data = { amount: 500 }
    const result = JSON.parse(buildPayload('bid.validated', 'auction-123', data))

    expect(result.type).toBe('bid.validated')
    expect(result.auctionId).toBe('auction-123')
    expect(result.data).toEqual(data)
    expect(result.sentAt).toBeDefined()
  })

  test('buildPayload deve sempre incluir sentAt como ISO string', () => {
    const result = JSON.parse(buildPayload('auction.closed', 'auction-456', {}))

    expect(() => new Date(result.sentAt)).not.toThrow()
    expect(new Date(result.sentAt).toISOString()).toBe(result.sentAt)
  })
})