import { create } from "zustand";
import { API_BASE_URL } from "./config";

export type Role = "TRANSPORTADORA" | "ADMIN";

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
}

export interface Bid {
  id: string;
  carrier: string;
  value: number;
  timestamp: string;
  position?: number;
}

export interface Cargo {
  id: string;
  description: string;
  origin: string;
  destination: string;
  weight: number;
  cargoType: string;
  createdAt: string;
}

export interface Auction {
  id: string;
  cargo: Cargo;
  initialValue: number;
  bestBid: number;
  leader: string | null;
  status: "ABERTO" | "ENCERRADO";
  bids: Bid[];
  endsAt: number; // epoch ms
  createdAt: string;
  winner?: string;
  events?: AuditEvent[];
}

export type AuditEventType =
  | "AUCTION_OPENED"
  | "BID_RECEIVED"
  | "BID_VALIDATED"
  | "BID_REJECTED"
  | "LEADER_CHANGED"
  | "AUCTION_CLOSED";

export interface AuditEvent {
  id: string;
  type: AuditEventType;
  service: "AUCTION_SERVICE" | "BID_SERVICE" | "NOTIFICATION_SERVICE";
  description: string;
  timestamp: string;
}

export interface Carrier {
  id: string;
  name: string;
  email: string;
  bidsSent: number;
  winRate: number;
  status: "ATIVA" | "INATIVA";
  createdAt: string;
}

interface StoreState {
  token: string | null;
  user: User | null;
  auctions: Auction[];
  cargos: Cargo[];
  carriers: Carrier[];
  wsStatus: "connecting" | "open" | "closed";
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
  setAuctions: (auctions: Auction[]) => void;
  setCargos: (cargos: Cargo[]) => void;
  setCarriers: (carriers: Carrier[]) => void;
  updateProfile: (patch: { name?: string; email?: string }) => void;
  addBidToAuction: (auctionId: string, bid: Bid) => void;
  closeAuction: (auctionId: string) => void;
  createAuction: (params: {
    name: string;
    origin: string;
    destination: string;
    cargoTypes: string[];
    initialValue: number;
    durationMin: number;
  }) => void;
  setWsStatus: (s: StoreState["wsStatus"]) => void;
  tick: () => void;
}

import { seedAuctions, seedCargos, seedCarriers } from "./mock-data";

interface AuthResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  userId: string;
  role: Role;
}

interface StoredSession {
  token: string;
  user: User;
}

function readStoredSession(): StoredSession | null {
  if (typeof window === "undefined") return null;

  try {
    const raw = window.localStorage.getItem("freightbid.session");
    if (!raw) return null;
    const session = JSON.parse(raw) as StoredSession;
    if (!session.token || !session.user) return null;
    return session;
  } catch {
    return null;
  }
}

function writeStoredSession(session: StoredSession | null) {
  if (typeof window === "undefined") return;

  if (!session) {
    window.localStorage.removeItem("freightbid.session");
    return;
  }

  window.localStorage.setItem("freightbid.session", JSON.stringify(session));
}

async function readAuthError(response: Response) {
  try {
    const data = await response.json();
    return data.message ?? data.error ?? "Falha ao fazer login";
  } catch {
    return "Falha ao fazer login";
  }
}

const storedSession = readStoredSession();

export const useStore = create<StoreState>((set, get) => ({
  token: storedSession?.token ?? null,
  user: storedSession?.user ?? null,
  auctions: seedAuctions(),
  cargos: seedCargos(),
  carriers: seedCarriers(),
  wsStatus: "closed",

  login: async (email, password) => {
    const response = await fetch(`${API_BASE_URL}/v1/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      throw new Error(await readAuthError(response));
    }

    const data = (await response.json()) as AuthResponse;
    const role = data.role;
    const name = role === "ADMIN" ? "Admin Operações" : "Transportadora";
    const user: User = {
      id: data.userId,
      name,
      email,
      role,
    };
    set({ user, token: data.token });
    writeStoredSession({ user, token: data.token });
    return user;
  },

  logout: () => {
    writeStoredSession(null);
    set({ user: null, token: null });
  },

  setAuctions: (auctions) => set({ auctions }),
  setCargos: (cargos) => set({ cargos }),
  setCarriers: (carriers) => set({ carriers }),

  updateProfile: (patch) =>
    set((state) => ({ user: state.user ? { ...state.user, ...patch } : state.user })),

  addBidToAuction: (auctionId, bid) => {
    set((state) => ({
      auctions: state.auctions.map((a) => {
        if (a.id !== auctionId) return a;
        if (a.status !== "ABERTO") return a;
        if (bid.value >= a.bestBid) {
          const ev: AuditEvent = {
            id: crypto.randomUUID(),
            type: "BID_REJECTED",
            service: "BID_SERVICE",
            description: `Lance rejeitado (R$ ${bid.value.toFixed(2)} >= melhor lance)`,
            timestamp: new Date().toISOString(),
          };
          return { ...a, events: [...(a.events ?? []), ev] };
        }
        const prevLeader = a.leader;
        const newBids = [...a.bids, bid];
        const events: AuditEvent[] = [
          ...(a.events ?? []),
          {
            id: crypto.randomUUID(),
            type: "BID_RECEIVED",
            service: "BID_SERVICE",
            description: `Lance recebido: R$ ${bid.value.toFixed(2)} — ${bid.carrier}`,
            timestamp: bid.timestamp,
          },
          {
            id: crypto.randomUUID(),
            type: "BID_VALIDATED",
            service: "BID_SERVICE",
            description: `Lance validado como novo menor: R$ ${bid.value.toFixed(2)}`,
            timestamp: bid.timestamp,
          },
        ];
        if (prevLeader !== bid.carrier) {
          events.push({
            id: crypto.randomUUID(),
            type: "LEADER_CHANGED",
            service: "NOTIFICATION_SERVICE",
            description: `Nova liderança: ${bid.carrier} (R$ ${bid.value.toFixed(2)})`,
            timestamp: bid.timestamp,
          });
        }
        return {
          ...a,
          bestBid: bid.value,
          leader: bid.carrier,
          bids: newBids,
          events,
        };
      }),
    }));
  },

  closeAuction: (auctionId) => {
    set((state) => ({
      auctions: state.auctions.map((a) => {
        if (a.id !== auctionId) return a;
        const events: AuditEvent[] = [
          ...(a.events ?? []),
          {
            id: crypto.randomUUID(),
            type: "AUCTION_CLOSED",
            service: "AUCTION_SERVICE",
            description: `Leilão encerrado. Vencedor: ${a.leader ?? "—"}`,
            timestamp: new Date().toISOString(),
          },
        ];
        return { ...a, status: "ENCERRADO", winner: a.leader ?? undefined, events };
      }),
    }));
  },

  createAuction: ({ name, origin, destination, cargoTypes, initialValue, durationMin }) => {
    const cargoId = "CARGO-" + Math.floor(1000 + Math.random() * 9000);
    const cargo: Cargo = {
      id: cargoId,
      description: name,
      origin,
      destination,
      weight: 0,
      cargoType: cargoTypes.join(", "),
      createdAt: new Date().toISOString(),
    };
    const id = "AUC-" + Math.floor(1000 + Math.random() * 9000);
    const now = Date.now();
    set((state) => ({
      cargos: [cargo, ...state.cargos],
      auctions: [
        {
          id,
          cargo,
          initialValue,
          bestBid: initialValue,
          leader: null,
          status: "ABERTO",
          bids: [],
          endsAt: now + durationMin * 60 * 1000,
          createdAt: new Date().toISOString(),
          events: [
            {
              id: crypto.randomUUID(),
              type: "AUCTION_OPENED",
              service: "AUCTION_SERVICE",
              description: `Leilão "${name}" aberto com valor inicial R$ ${initialValue.toFixed(2)}`,
              timestamp: new Date().toISOString(),
            },
          ],
        },
        ...state.auctions,
      ],
    }));
  },

  setWsStatus: (s) => set({ wsStatus: s }),

  tick: () => {
    const now = Date.now();
    const toClose = get().auctions.filter((a) => a.status === "ABERTO" && a.endsAt <= now);
    if (toClose.length > 0) {
      toClose.forEach((a) => get().closeAuction(a.id));
    }
  },
}));

export const formatBRL = (n: number) =>
  n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

export const formatNumber = (n: number) =>
  n.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
