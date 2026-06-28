import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = 'http://api-gateway:8080'

export function criarCarga(token) {
  const res = http.post(`${BASE_URL}/v1/loads`, JSON.stringify({
    origin: 'Recife',
    destination: 'Olinda',
    description: 'Carga k6',
    weightKg: 100,
    initialPrice: 1000,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  })

  check(res, { 'carga criada': (r) => r.status === 201 })
  if (res.status !== 201) return null

  return JSON.parse(res.body).id
}

export function criarLeilao(token, loadId, duracaoMinutos = 30) {
  const res = http.post(`${BASE_URL}/v1/auctions`, JSON.stringify({
    loadId,
    durationMinutes: duracaoMinutos,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  })

  check(res, { 'leilão criado': (r) => r.status === 201 })
  if (res.status !== 201) return null

  return JSON.parse(res.body).id
}