import { sleep, check } from 'k6'
import http from 'k6/http'
import { obterTokenAdmin, obterTokenTransportadora } from '../lib/auth.js'
import { criarCarga, criarLeilao } from '../lib/fixtures.js'

export const options = {
  stages: [
    { duration: '2m', target: 5  },
    { duration: '3m', target: 10 },
    { duration: '3m', target: 15 },
    { duration: '2m', target: 20 },
    { duration: '2m', target: 0  },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.05'],
    http_req_duration: ['p(95)<60000', 'p(99)<65000'],
    },
}

export function setup() {
  const tokenAdmin = obterTokenAdmin()
  const loadId = criarCarga(tokenAdmin)
  const id = criarLeilao(tokenAdmin, loadId)
  return { auctionId: id }
}

export default function (data) {
  const tokenTransportadora = obterTokenTransportadora(`${__VU}-${__ITER}`)
  if (!tokenTransportadora) return

  const amount = Math.floor(Math.random() * 500) + 500

  const res = http.post('http://api-gateway:8080/v1/bids', JSON.stringify({
    auctionId: data.auctionId,
    amount,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${tokenTransportadora}`,
    },
  })

  check(res, { 'lance aceito': (r) => r.status === 202 || r.status === 409 })

  sleep(1)
}