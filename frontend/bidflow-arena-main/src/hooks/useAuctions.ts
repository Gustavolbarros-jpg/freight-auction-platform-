import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";

import { apiFetch } from "@/lib/api";
import { useStore, type Auction, type Cargo } from "@/lib/store";

interface AuctionResponse {
  id: string;
  loadId: string;
  status: "OPEN" | "CLOSED";
  initialPrice: number | string;
  startedAt: string;
  durationMinutes?: number | string | null;
  closedAt: string | null;
  createdByUserId: string;
  winnerCarrierId: string | null;
  winningAmount: number | string | null;
}

interface LoadResponse {
  id: string;
  origin: string;
  destination: string;
  description: string;
  weightKg: number | string;
  initialPrice: number | string;
  createdByUserId: string;
  createdAt: string;
}

const DEFAULT_DURATION_MINUTES = 60;

function toNumber(value: number | string | null | undefined, fallback = 0) {
  if (value === null || value === undefined) return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function shortCarrierName(carrierId: string | null) {
  if (!carrierId) return null;
  return `TRP-${carrierId.slice(0, 8)}`;
}

function mapLoadToCargo(load: LoadResponse): Cargo {
  return {
    id: load.id,
    description: load.description,
    origin: load.origin,
    destination: load.destination,
    weight: toNumber(load.weightKg),
    cargoType: "Frete",
    createdAt: load.createdAt,
  };
}

function mapAuctionToFrontend(
  auction: AuctionResponse,
  loadById: Map<string, LoadResponse>,
): Auction {
  const initialValue = toNumber(auction.initialPrice);
  const bestBid = toNumber(auction.winningAmount, initialValue);
  const startedAt = new Date(auction.startedAt).getTime();
  const load = loadById.get(auction.loadId);

  return {
    id: auction.id,
    cargo: load
      ? mapLoadToCargo(load)
      : {
          id: auction.loadId,
          description: "Carga não encontrada",
          origin: "Origem não informada",
          destination: "Destino não informado",
          weight: 0,
          cargoType: "Frete",
          createdAt: auction.startedAt,
        },
    initialValue,
    bestBid,
    leader: shortCarrierName(auction.winnerCarrierId),
    status: auction.status === "OPEN" ? "ABERTO" : "ENCERRADO",
    bids: [],
    endsAt: startedAt + toNumber(auction.durationMinutes, DEFAULT_DURATION_MINUTES) * 60 * 1000,
    createdAt: auction.startedAt,
    winner: shortCarrierName(auction.winnerCarrierId) ?? undefined,
    events: [],
  };
}

async function fetchAuctions() {
  const auctions = await apiFetch<AuctionResponse[]>("/v1/auctions");
  const loadIds = [...new Set(auctions.map((auction) => auction.loadId))];
  const loads = await Promise.all(
    loadIds.map((loadId) => apiFetch<LoadResponse>(`/v1/loads/${loadId}`)),
  );
  const loadById = new Map(loads.map((load) => [load.id, load]));

  return auctions.map((auction) => mapAuctionToFrontend(auction, loadById));
}

export function useAuctions() {
  const token = useStore((state) => state.token);
  const setAuctions = useStore((state) => state.setAuctions);

  const query = useQuery({
    queryKey: ["auctions"],
    queryFn: fetchAuctions,
    enabled: Boolean(token),
  });

  useEffect(() => {
    if (query.data) {
      setAuctions(query.data);
    }
  }, [query.data, setAuctions]);

  return query;
}
