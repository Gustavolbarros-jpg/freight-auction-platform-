import http from 'k6/http'
import { check } from 'k6'

const BASE_URL = 'http://api-gateway:8080'

http.setResponseCallback(http.expectedStatuses(200, 201, 202, 204, 409))

export function registrar(nome, email, senha, role) {
  const res = http.post(`${BASE_URL}/v1/auth/register`, JSON.stringify({
    name: nome,
    email,
    password: senha,
    role,
  }), { headers: { 'Content-Type': 'application/json' } })

  check(res, { 'registro ok': (r) => r.status === 201 || r.status === 200 || r.status === 409 })
  return res
}

export function login(email, senha) {
  const res = http.post(`${BASE_URL}/v1/auth/login`, JSON.stringify({
    email,
    password: senha,
  }), { headers: { 'Content-Type': 'application/json' } })

  check(res, { 'login ok': (r) => r.status === 200 })
  if (res.status !== 200) return null

  return JSON.parse(res.body).token
}

export function obterTokenAdmin() {
  const email = 'admin-k6@example.com'
  const senha = 'senha123'
  registrar('Admin K6', email, senha, 'ADMIN')
  return login(email, senha)
}

export function obterTokenTransportadora(sufixo) {
  const email = `transportadora-k6-${sufixo}@example.com`
  const senha = 'senha123'
  registrar(`Transportadora K6 ${sufixo}`, email, senha, 'TRANSPORTADORA')
  return login(email, senha)
}