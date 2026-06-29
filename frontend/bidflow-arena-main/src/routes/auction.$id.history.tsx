import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowLeft, CheckCircle2, Play, ArrowDown, Check, X, Crown, Square } from "lucide-react";
import { AppShell } from "@/components/app-shell";
import { useAuctionBids, type BidResponse } from "@/hooks/useAuctionBids";
import { useStore, formatBRL, type AuditEvent, type AuditEventType } from "@/lib/store";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/auction/$id/history")({
  head: () => ({
    meta: [
      { title: "Histórico do Leilão — FreightBid" },
      { name: "description", content: "Auditoria completa do leilão." },
    ],
  }),
  component: AuctionHistoryPage,
});

function AuctionHistoryPage() {
  return (
    <AppShell requireAdmin>
      <HistoryContent />
    </AppShell>
  );
}

function HistoryContent() {
  const { id } = Route.useParams();
  const { data: bids = [], isError, isLoading } = useAuctionBids(id);
  const auction = useStore((s) => s.auctions.find((a) => a.id === id));

  if (!auction) {
    return (
      <div className="rounded-xl border border-border bg-[var(--surface)] p-12 text-center text-muted-foreground">
        Leilão não encontrado.
      </div>
    );
  }

  const events = buildBidEvents(bids);
  const received = bids.length;
  const validated = bids.filter(
    (bid) => bid.status === "VALIDATED" || bid.status === "WINNING",
  ).length;
  const rejected = bids.filter((bid) => bid.status === "REJECTED").length;
  const reduction =
    auction.initialValue > 0
      ? Math.round(((auction.initialValue - auction.bestBid) / auction.initialValue) * 1000) / 10
      : 0;
  const durationMin = Math.max(
    1,
    Math.round((auction.endsAt - new Date(auction.createdAt).getTime()) / 60000),
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Link
          to="/admin"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          Admin
        </Link>
        <div className="h-4 w-px bg-border" />
        <h1 className="text-xl font-semibold">Auditoria — {auction.id}</h1>
        <span
          className={cn(
            "rounded-full px-2 py-0.5 text-[11px] font-medium",
            auction.status === "ENCERRADO"
              ? "bg-muted text-muted-foreground"
              : "bg-success/15 text-success",
          )}
        >
          {auction.status}
        </span>
      </div>

      {auction.winner && (
        <div className="rounded-xl border border-success/40 bg-success/10 p-5">
          <div className="flex items-center gap-3">
            <CheckCircle2 className="h-6 w-6 text-success" />
            <div className="flex-1">
              <div className="text-xs uppercase tracking-wider text-success">Vencedor</div>
              <div className="text-lg font-semibold">{auction.winner}</div>
              <div className="text-xs text-muted-foreground">
                {auction.cargo.origin} → {auction.cargo.destination}
              </div>
            </div>
            <div className="text-right">
              <div className="text-[11px] uppercase tracking-wider text-muted-foreground">
                Preço Final
              </div>
              <div className="font-mono-tnum text-2xl font-bold text-success">
                {formatBRL(auction.bestBid)}
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
        <StatBox label="Lances Recebidos" value={received} />
        <StatBox label="Validados" value={validated} accent="success" />
        <StatBox label="Rejeitados" value={rejected} accent="destructive" />
        <StatBox label="Duração" value={`${durationMin}m`} />
        <StatBox label="Redução" value={`${reduction}%`} accent="primary" />
      </div>

      <div className="rounded-xl border border-border bg-[var(--surface)] p-6">
        <h2 className="mb-6 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Linha do Tempo de Eventos
        </h2>
        {isLoading && (
          <div className="rounded-md border border-border bg-background px-3 py-2 text-sm text-muted-foreground">
            Carregando histórico de lances...
          </div>
        )}
        {isError && (
          <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            Não foi possível carregar o histórico real de lances.
          </div>
        )}
        {!isLoading && !isError && events.length === 0 && (
          <div className="rounded-md border border-border bg-background px-3 py-2 text-sm text-muted-foreground">
            Nenhum lance registrado para este leilão ainda.
          </div>
        )}
        {!isLoading && !isError && events.length > 0 && (
          <div className="relative">
            <div className="absolute left-1/2 top-0 bottom-0 w-px -translate-x-1/2 bg-border" />
            <div className="space-y-3">
              {events.map((ev, i) => {
                const meta = EVENT_META[ev.type];
                const left = i % 2 === 0;
                return (
                  <div
                    key={ev.id}
                    className="relative grid grid-cols-[1fr_40px_1fr] items-start gap-3"
                  >
                    <div
                      className={cn(
                        "text-xs text-muted-foreground",
                        left ? "text-right" : "opacity-0 pointer-events-none",
                      )}
                    >
                      {left && (
                        <>
                          <div className="font-mono-tnum">
                            {new Date(ev.timestamp).toLocaleTimeString("pt-BR")}
                          </div>
                          <div className="mt-0.5 text-[10px] uppercase tracking-wider">
                            {ev.service}
                          </div>
                        </>
                      )}
                    </div>
                    <div className="relative flex justify-center">
                      <div
                        className={cn(
                          "relative z-10 flex h-8 w-8 items-center justify-center rounded-full ring-4 ring-[var(--surface)]",
                          meta.bg,
                        )}
                      >
                        <meta.icon className="h-3.5 w-3.5 text-white" />
                      </div>
                    </div>
                    <div className={cn(left ? "opacity-0 pointer-events-none" : "")}>
                      {!left && (
                        <>
                          <div className="text-xs text-muted-foreground">
                            <span className="font-mono-tnum">
                              {new Date(ev.timestamp).toLocaleTimeString("pt-BR")}
                            </span>
                            {" · "}
                            <span className="uppercase tracking-wider">{ev.service}</span>
                          </div>
                          <div className="mt-0.5 rounded-md border border-border bg-background px-3 py-2 text-sm">
                            {ev.description}
                          </div>
                        </>
                      )}
                    </div>
                    {/* Reverse layout description on left side */}
                    {left && (
                      <div className="absolute right-[calc(50%+28px)] top-0 max-w-[45%]">
                        <div className="rounded-md border border-border bg-background px-3 py-2 text-sm text-right">
                          {ev.description}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function buildBidEvents(bids: BidResponse[]): AuditEvent[] {
  return bids
    .slice()
    .sort((a, b) => new Date(a.receivedAt).getTime() - new Date(b.receivedAt).getTime())
    .map((bid) => {
      const amount = Number(bid.amount);
      const carrier = `TRP-${bid.carrierId.slice(0, 8)}`;
      const type = mapBidStatusToEventType(bid.status);

      return {
        id: bid.id,
        type,
        service: "BID_SERVICE",
        description: `${bidStatusLabel(bid.status)}: ${formatBRL(amount)} — ${carrier}`,
        timestamp: bid.receivedAt,
      };
    });
}

function mapBidStatusToEventType(status: BidResponse["status"]): AuditEventType {
  if (status === "VALIDATED" || status === "WINNING") return "BID_VALIDATED";
  if (status === "REJECTED") return "BID_REJECTED";
  return "BID_RECEIVED";
}

function bidStatusLabel(status: BidResponse["status"]) {
  if (status === "VALIDATED" || status === "WINNING") return "Lance validado";
  if (status === "REJECTED") return "Lance rejeitado";
  return "Lance recebido";
}

const EVENT_META = {
  AUCTION_OPENED: { icon: Play, bg: "bg-primary" },
  BID_RECEIVED: { icon: ArrowDown, bg: "bg-muted-foreground" },
  BID_VALIDATED: { icon: Check, bg: "bg-success" },
  BID_REJECTED: { icon: X, bg: "bg-destructive" },
  LEADER_CHANGED: { icon: Crown, bg: "bg-gold" },
  AUCTION_CLOSED: { icon: Square, bg: "bg-foreground" },
} as const;

function StatBox({
  label,
  value,
  accent,
}: {
  label: string;
  value: number | string;
  accent?: "success" | "destructive" | "primary";
}) {
  const cls =
    accent === "success"
      ? "text-success"
      : accent === "destructive"
        ? "text-destructive"
        : accent === "primary"
          ? "text-primary"
          : "text-foreground";
  return (
    <div className="rounded-xl border border-border bg-[var(--surface)] p-4">
      <div className="text-xs uppercase tracking-wider text-muted-foreground">{label}</div>
      <div className={cn("mt-1 font-mono-tnum text-2xl font-semibold", cls)}>{value}</div>
    </div>
  );
}
