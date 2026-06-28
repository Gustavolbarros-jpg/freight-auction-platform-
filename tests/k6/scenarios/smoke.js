import { sleep, check } from 'k6'
import http from 'k6/http'
import { obterTokenAdmin, obterTokenTransportadora } from '../lib/auth.js'
import { criarCarga, criarLeilao } from '../lib/fixtures.js'

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
}

export function setup() {
  const tokenAdmin = obterTokenAdmin()
  const loadId = criarCarga(tokenAdmin)
  const auctionId = criarLeilao(tokenAdmin, loadId)
  return { auctionId, tokenAdmin }
}

export default function (data) {
  const tokenTransportadora = obterTokenTransportadora(`${__VU}-${__ITER}`)
  if (!tokenTransportadora) return

  const resBid = http.post('http://api-gateway:8080/v1/bids', JSON.stringify({
    auctionId: data.auctionId,
    amount: Math.floor(Math.random() * 500) + 500,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${tokenTransportadora}`,
    },
  })

  check(resBid, { 'lance aceito': (r) => r.status === 202 || r.status === 409 })

  sleep(1)

  const resBest = http.get(`http://api-gateway:8080/v1/bids/auctions/${data.auctionId}/best`, {
    headers: { 'Authorization': `Bearer ${tokenTransportadora}` },
  })

  check(resBest, { 'melhor lance retornado': (r) => r.status === 200 })

  sleep(1)
}