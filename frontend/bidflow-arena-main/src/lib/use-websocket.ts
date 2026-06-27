import { useEffect, useRef } from "react";
import { useStore, type Bid } from "./store";

interface WebSocketEnvelope {
  type: "bid.validated" | "auction.closed" | "auction.opened";
  auctionId?: string;
  data?: {
    auctionId?: string;
    bidId?: string;
    carrierId?: string;
    amount?: number | string;
    receivedAt?: string;
    status?: string;
    closedAt?: string;
    winnerCarrierId?: string;
    winningAmount?: number | string;
    loadId?: string;
    startedAt?: string;
  };
  sentAt?: string;
}

function carrierLabel(carrierId?: string) {
  if (!carrierId) return "Transportadora";
  return `TRP-${carrierId.slice(0, 8)}`;
}

export function useAuctionWebSocket(auctionId: string) {
  const setWsStatus = useStore((s) => s.setWsStatus);
  const addBid = useStore((s) => s.addBidToAuction);
  const closeAuction = useStore((s) => s.closeAuction);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;

    let cancelled = false;
    setWsStatus("connecting");

    const tryConnect = () => {
      try {
        const ws = new WebSocket(`ws://localhost:8083?auction=${auctionId}`);
        wsRef.current = ws;

        ws.onopen = () => setWsStatus("open");

        ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data) as WebSocketEnvelope;
            const data = message.data ?? {};
            const eventAuctionId = data.auctionId ?? message.auctionId ?? auctionId;

            if (eventAuctionId !== auctionId && message.type !== "auction.opened") {
              return;
            }

            if (message.type === "bid.validated") {
              if (!data.bidId || !data.amount) return;

              const bid: Bid = {
                id: data.bidId,
                carrier: carrierLabel(data.carrierId),
                value: Number(data.amount),
                timestamp: data.receivedAt ?? message.sentAt ?? new Date().toISOString(),
              };

              addBid(eventAuctionId, bid);
            }

            if (message.type === "auction.closed") {
              closeAuction(eventAuctionId);
            }

            if (message.type === "auction.opened") {
              // Por enquanto não faz nada aqui.
              // Depois vamos usar isso para atualizar/refetch da lista.
            }
          } catch (error) {
            console.error("Erro ao processar mensagem WebSocket", error);
          }
        };

        ws.onclose = () => {
          setWsStatus("closed");
          if (!cancelled) setTimeout(tryConnect, 3000);
        };

        ws.onerror = () => ws.close();
      } catch {
        setWsStatus("closed");
      }
    };

    tryConnect();

    return () => {
      cancelled = true;
      wsRef.current?.close();
    };
  }, [auctionId, setWsStatus, addBid, closeAuction]);
}

/** Global timer tick to auto-close expired auctions */
export function useGlobalTick() {
  const tick = useStore((s) => s.tick);

  useEffect(() => {
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [tick]);
}