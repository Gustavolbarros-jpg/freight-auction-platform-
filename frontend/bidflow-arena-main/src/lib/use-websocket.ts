import { useEffect, useRef } from "react";
import { toast } from "sonner";
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
  const carrier = useStore.getState().carriers.find((item) => item.id === carrierId);
  if (carrier) return carrier.name;
  return `TRP-${carrierId.slice(0, 8)}`;
}

function notifyAuctionClosed(message: WebSocketEnvelope) {
  const data = message.data ?? {};
  const auctionId = data.auctionId ?? message.auctionId;
  const user = useStore.getState().user;
  const addNotification = useStore.getState().addNotification;
  const winner = carrierLabel(data.winnerCarrierId) || "sem vencedor";
  const winningAmount = data.winningAmount ? Number(data.winningAmount) : null;
  const winningText =
    winningAmount && Number.isFinite(winningAmount)
      ? ` por ${winningAmount.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}`
      : "";

  if (user?.role === "TRANSPORTADORA" && winner === user.name) {
    addNotification({
      kind: "AUCTION_CLOSED",
      auctionId,
      title: "Você venceu o leilão",
      description: `Campeão: ${winner}${winningText}.`,
    });
    toast.success("Você venceu o leilão", {
      description: `Campeão: ${winner}${winningText}.`,
    });
    return;
  }

  addNotification({
    kind: "AUCTION_CLOSED",
    auctionId,
    title: "Leilão encerrado",
    description: `Campeão: ${winner}${winningText}.`,
  });
  toast.info("Leilão encerrado", {
    description: `Campeão: ${winner}${winningText}.`,
  });
}

function notifyAuctionOpened(message: WebSocketEnvelope) {
  const data = message.data ?? {};
  const auctionId = data.auctionId ?? message.auctionId;
  const description = auctionId
    ? `Leilão ${auctionId.slice(0, 8)} foi criado e já está disponível.`
    : "Um novo leilão foi criado e já está disponível.";

  useStore.getState().addNotification({
    kind: "AUCTION_OPENED",
    auctionId,
    title: "Novo leilão criado",
    description,
  });
  toast.info("Novo leilão criado", { description });
}

function handleRealtimeMessage(message: WebSocketEnvelope, updateStore: boolean) {
  const data = message.data ?? {};
  const eventAuctionId = data.auctionId ?? message.auctionId;

  if (message.type === "bid.validated") {
    if (!eventAuctionId || !data.bidId || !data.amount) return;

    if (updateStore) {
      const bid: Bid = {
        id: data.bidId,
        carrier: carrierLabel(data.carrierId),
        value: Number(data.amount),
        timestamp: data.receivedAt ?? message.sentAt ?? new Date().toISOString(),
      };

      useStore.getState().addBidToAuction(eventAuctionId, bid);
    }
  }

  if (message.type === "auction.closed") {
    notifyAuctionClosed(message);
    if (updateStore && eventAuctionId) {
      useStore.getState().closeAuction(eventAuctionId);
    }
  }

  if (message.type === "auction.opened") {
    notifyAuctionOpened(message);
  }
}

export function useGlobalNotifications() {
  const setWsStatus = useStore((s) => s.setWsStatus);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;

    let cancelled = false;
    setWsStatus("connecting");

    const tryConnect = () => {
      try {
        const ws = new WebSocket("ws://localhost:8083");
        wsRef.current = ws;

        ws.onopen = () => setWsStatus("open");

        ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data) as WebSocketEnvelope;
            handleRealtimeMessage(message, true);
          } catch (error) {
            console.error("Erro ao processar notificação global WebSocket", error);
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
  }, [setWsStatus]);
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
