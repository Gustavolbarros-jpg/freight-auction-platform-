import type { Auction, Cargo, Carrier, Bid, AuditEvent } from "./store";

const CARRIERS = [
  "Logística Norte Ltda",
  "TransBrasil Express",
  "Expresso Sul Transportes",
  "Rápido Cargo SP",
  "NordLog Transportes",
  "Bahia Frete Rápido",
  "Centro-Oeste Log",
  "Atlas Transportes",
];

export const MOCK_CARRIER_NAMES = CARRIERS;

const ROUTES: Array<[string, string, number, number]> = [
  // [origin, destination, weight kg, initialPrice]
  ["São Paulo - SP", "Recife - PE", 12500, 14500],
  ["Curitiba - PR", "Fortaleza - CE", 8200, 16800],
  ["Porto Alegre - RS", "Manaus - AM", 6500, 17900],
  ["Brasília - DF", "Salvador - BA", 9800, 7200],
  ["Belo Horizonte - MG", "Recife - PE", 11200, 9800],
  ["São Paulo - SP", "Curitiba - PR", 15400, 3400],
  ["Salvador - BA", "Fortaleza - CE", 7800, 4900],
  ["Manaus - AM", "Brasília - DF", 5600, 15200],
  ["Curitiba - PR", "São Paulo - SP", 18000, 2800],
  ["Belo Horizonte - MG", "Salvador - BA", 9200, 6400],
  ["Porto Alegre - RS", "São Paulo - SP", 13800, 5200],
  ["Fortaleza - CE", "Recife - PE", 6200, 2900],
];

const CARGO_TYPES = [
  "Carga Seca",
  "Refrigerada",
  "Produtos Químicos",
  "Eletrônicos",
  "Alimentos",
  "Materiais de Construção",
];

const DESCRIPTIONS = [
  "Carga consolidada de eletrodomésticos, paletizada, 24 paletes",
  "Carga refrigerada — laticínios e produtos cárneos",
  "Equipamentos industriais, embalagem reforçada",
  "Suprimentos hospitalares — entrega prioritária",
  "Insumos agrícolas — fertilizantes ensacados",
  "Bebidas — paletizado, fragilidade média",
  "Componentes automotivos — kits para montadora",
  "Móveis planejados embalados",
];

export function seedCargos(): Cargo[] {
  return ROUTES.map((r, i) => ({
    id: "CRG-" + (1001 + i),
    description: DESCRIPTIONS[i % DESCRIPTIONS.length],
    origin: r[0],
    destination: r[1],
    weight: r[2],
    cargoType: CARGO_TYPES[i % CARGO_TYPES.length],
    createdAt: new Date(Date.now() - (i + 1) * 86400000 * 0.5).toISOString(),
  }));
}

export function seedCarriers(): Carrier[] {
  return CARRIERS.map((name, i) => ({
    id: "TRP-" + (201 + i),
    name,
    email: name.toLowerCase().replace(/[^a-z]+/g, "") + "@freight.com.br",
    bidsSent: 40 + Math.floor(Math.random() * 180),
    winRate: Math.round((10 + Math.random() * 45) * 10) / 10,
    status: i === 7 ? "INATIVA" : "ATIVA",
    createdAt: new Date(Date.now() - (i + 5) * 86400000 * 7).toISOString(),
  }));
}

function rid() {
  return Math.random().toString(36).slice(2, 10);
}

export function seedAuctions(): Auction[] {
  const cargos = seedCargos();
  const now = Date.now();

  const buildHistoryBids = (
    initial: number,
    final: number,
    n: number,
    startMs: number,
  ): { bids: Bid[]; events: AuditEvent[] } => {
    const bids: Bid[] = [];
    const events: AuditEvent[] = [];
    events.push({
      id: rid(),
      type: "AUCTION_OPENED",
      service: "AUCTION_SERVICE",
      description: `Leilão aberto com valor inicial R$ ${initial.toFixed(2)}`,
      timestamp: new Date(startMs).toISOString(),
    });
    let current = initial;
    let leader = "";
    for (let i = 0; i < n; i++) {
      const carrier = CARRIERS[Math.floor(Math.random() * CARRIERS.length)];
      const t = new Date(startMs + ((i + 1) * (1800000 / n))).toISOString();
      // Sometimes reject
      if (Math.random() < 0.2 && i > 0) {
        const badValue = current + Math.random() * 200;
        events.push({
          id: rid(),
          type: "BID_RECEIVED",
          service: "BID_SERVICE",
          description: `Lance recebido: R$ ${badValue.toFixed(2)} — ${carrier}`,
          timestamp: t,
        });
        events.push({
          id: rid(),
          type: "BID_REJECTED",
          service: "BID_SERVICE",
          description: `Lance rejeitado (valor >= melhor lance)`,
          timestamp: t,
        });
        continue;
      }
      const decrement =
        i === n - 1
          ? current - final
          : Math.max(50, (current - final) / (n - i) + Math.random() * 100);
      current = Math.max(final, current - decrement);
      const value = Math.round(current * 100) / 100;
      bids.push({ id: rid(), carrier, value, timestamp: t });
      events.push({
        id: rid(),
        type: "BID_RECEIVED",
        service: "BID_SERVICE",
        description: `Lance recebido: R$ ${value.toFixed(2)} — ${carrier}`,
        timestamp: t,
      });
      events.push({
        id: rid(),
        type: "BID_VALIDATED",
        service: "BID_SERVICE",
        description: `Lance validado como novo menor: R$ ${value.toFixed(2)}`,
        timestamp: t,
      });
      if (carrier !== leader) {
        events.push({
          id: rid(),
          type: "LEADER_CHANGED",
          service: "NOTIFICATION_SERVICE",
          description: `Nova liderança: ${carrier} (R$ ${value.toFixed(2)})`,
          timestamp: t,
        });
        leader = carrier;
      }
    }
    return { bids, events };
  };

  // Two OPEN
  const open1Cargo = cargos[0];
  const open1Init = 14500;
  const open1Final = 11800;
  const open1History = buildHistoryBids(open1Init, open1Final, 6, now - 13 * 60 * 1000);
  const open1: Auction = {
    id: "AUC-1042",
    cargo: open1Cargo,
    initialValue: open1Init,
    bestBid: open1Final,
    leader: open1History.bids.at(-1)?.carrier ?? null,
    status: "ABERTO",
    bids: open1History.bids,
    endsAt: now + 47 * 60 * 1000,
    createdAt: new Date(now - 13 * 60 * 1000).toISOString(),
    events: open1History.events,
  };

  const open2Cargo = cargos[3];
  const open2Init = 7200;
  const open2Final = 5450;
  const open2History = buildHistoryBids(open2Init, open2Final, 8, now - 52 * 60 * 1000);
  const open2: Auction = {
    id: "AUC-1043",
    cargo: open2Cargo,
    initialValue: open2Init,
    bestBid: open2Final,
    leader: open2History.bids.at(-1)?.carrier ?? null,
    status: "ABERTO",
    bids: open2History.bids,
    endsAt: now + 8 * 60 * 1000,
    createdAt: new Date(now - 52 * 60 * 1000).toISOString(),
    events: open2History.events,
  };

  const closedAuctions: Auction[] = [4, 5, 6].map((idx, i) => {
    const cargo = cargos[idx];
    const initial = [9800, 3400, 4900][i];
    const final = [7200, 2750, 3850][i];
    const history = buildHistoryBids(initial, final, 10, now - (i + 2) * 86400000);
    const winner = history.bids.at(-1)?.carrier ?? CARRIERS[0];
    history.events.push({
      id: rid(),
      type: "AUCTION_CLOSED",
      service: "AUCTION_SERVICE",
      description: `Leilão encerrado. Vencedor: ${winner}`,
      timestamp: new Date(now - (i + 2) * 86400000 + 1800000).toISOString(),
    });
    return {
      id: "AUC-" + (1030 + i),
      cargo,
      initialValue: initial,
      bestBid: final,
      leader: winner,
      winner,
      status: "ENCERRADO",
      bids: history.bids,
      endsAt: now - (i + 2) * 86400000 + 1800000,
      createdAt: new Date(now - (i + 2) * 86400000).toISOString(),
      events: history.events,
    };
  });

  return [open1, open2, ...closedAuctions];
}
