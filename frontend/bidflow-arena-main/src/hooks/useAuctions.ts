import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";

import { apiFetch } from "@/lib/api";
import {
  formatBRL,
  useStore,
  type Auction,
  type AuditEvent,
  type Bid,
  type Cargo,
} from "@/lib/store";

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

interface BidResponse {
  id: string;
  auctionId: string;
  carrierId: string;
  amount: number | string;
  status: "RECEIVED" | "VALIDATED" | "REJECTED" | "WINNING";
  receivedAt: string;
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

function isAcceptedBid(status: BidResponse["status"]) {
  return status === "VALIDATED" || status === "WINNING";
}

function mapBidToFrontend(bid: BidResponse): Bid {
  return {
    id: bid.id,
    carrier: shortCarrierName(bid.carrierId) ?? "Transportadora",
    value: toNumber(bid.amount),
    timestamp: bid.receivedAt,
  };
}

function mapBidToEvent(bid: BidResponse): AuditEvent {
  const amount = toNumber(bid.amount);
  const carrier = shortCarrierName(bid.carrierId) ?? "Transportadora";
  const accepted = isAcceptedBid(bid.status);

  return {
    id: bid.id,
    type: accepted ? "BID_VALIDATED" : bid.status === "REJECTED" ? "BID_REJECTED" : "BID_RECEIVED",
    service: "BID_SERVICE",
    description: `${accepted ? "Lance validado" : bid.status === "REJECTED" ? "Lance rejeitado" : "Lance recebido"}: ${formatBRL(amount)} — ${carrier}`,
    timestamp: bid.receivedAt,
  };
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
  bidsByAuctionId: Map<string, BidResponse[]>,
): Auction {
  const initialValue = toNumber(auction.initialPrice);
  const savedBids = bidsByAuctionId.get(auction.id) ?? [];
  const acceptedBids = savedBids.filter((bid) => isAcceptedBid(bid.status));
  const bestSavedBid = acceptedBids
    .map((bid) => mapBidToFrontend(bid))
    .sort((a, b) => a.value - b.value)[0];
  const bestBid = toNumber(auction.winningAmount, bestSavedBid?.value ?? initialValue);
  const startedAt = new Date(auction.startedAt).getTime();
  const load = loadById.get(auction.loadId);
  const leader = shortCarrierName(auction.winnerCarrierId) ?? bestSavedBid?.carrier ?? null;

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
    leader,
    status: auction.status === "OPEN" ? "ABERTO" : "ENCERRADO",
    bids: acceptedBids.map(mapBidToFrontend),
    endsAt: startedAt + toNumber(auction.durationMinutes, DEFAULT_DURATION_MINUTES) * 60 * 1000,
    createdAt: auction.startedAt,
    winner: leader ?? undefined,
    events: savedBids.map(mapBidToEvent),
  };
}

async function fetchAuctions() {
  const auctions = await apiFetch<AuctionResponse[]>("/v1/auctions");
  const loadIds = [...new Set(auctions.map((auction) => auction.loadId))];
  const loads = await Promise.all(
    loadIds.map((loadId) => apiFetch<LoadResponse>(`/v1/loads/${loadId}`)),
  );
  const loadById = new Map(loads.map((load) => [load.id, load]));
  const bidsByAuctionId = new Map(
    await Promise.all(
      auctions.map(async (auction) => {
        const bids = await apiFetch<BidResponse[]>(`/v1/bids/auctions/${auction.id}`);
        return [auction.id, bids] as const;
      }),
    ),
  );

  return auctions.map((auction) => mapAuctionToFrontend(auction, loadById, bidsByAuctionId));
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
