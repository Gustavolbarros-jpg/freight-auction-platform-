import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import {
  Package,
  MapPin,
  Scale,
  Send,
  Crown,
  BarChart2,
  Truck,
  CheckCircle2,
  Loader2,
} from "lucide-react";
import { toast } from "sonner";
import { AppShell } from "@/components/app-shell";
import { useAuctions } from "@/hooks/useAuctions";
import { useStore, formatBRL, type Bid } from "@/lib/store";
import { useAuctionWebSocket } from "@/lib/use-websocket";
import { StatusBadge } from "./dashboard";
import { cn } from "@/lib/utils";
import { apiFetch } from "@/lib/api";

export const Route = createFileRoute("/auction/$id")({
  head: () => ({
    meta: [
      { title: "Sala de Leilão — FreightBid" },
      { name: "description", content: "Sala de leilão ao vivo com lances em tempo real." },
    ],
  }),
  component: AuctionRoom,
});

function AuctionRoom() {
  return (
    <AppShell>
      <AuctionContent />
    </AppShell>
  );
}

function AuctionContent() {
  const { id } = Route.useParams();
  useAuctions();
  const auction = useStore((s) => s.auctions.find((a) => a.id === id));
  const user = useStore((s) => s.user);
  const wsStatus = useStore((s) => s.wsStatus);
  useAuctionWebSocket(id);

  const [bidValue, setBidValue] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [flashKey, setFlashKey] = useState(0);
  const [prevBest, setPrevBest] = useState(auction?.bestBid ?? 0);
  const [, force] = useState(0);

  useEffect(() => {
    const t = setInterval(() => force((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (!auction) return;
    if (auction.bestBid !== prevBest) {
      setFlashKey((k) => k + 1);
      setPrevBest(auction.bestBid);
    }
  }, [auction?.bestBid, prevBest, auction]);

  const ranking = useMemo(() => {
    if (!auction) return [];
    const bestPerCarrier = new Map<string, Bid>();
    for (const b of auction.bids) {
      const cur = bestPerCarrier.get(b.carrier);
      if (!cur || b.value < cur.value) bestPerCarrier.set(b.carrier, b);
    }
    return [...bestPerCarrier.values()].sort((a, b) => a.value - b.value);
  }, [auction]);

  const recentEvents = useMemo(() => (auction?.events ?? []).slice(-12).reverse(), [auction]);

  if (!auction) {
    return (
      <div className="rounded-xl border border-border bg-[var(--surface)] p-12 text-center text-muted-foreground">
        Leilão não encontrado.
      </div>
    );
  }

  const remaining = Math.max(0, Math.floor((auction.endsAt - Date.now()) / 1000));
  const mm = String(Math.floor(remaining / 60)).padStart(2, "0");
  const ss = String(remaining % 60).padStart(2, "0");
  const urgent = remaining < 120 && auction.status === "ABERTO";
  const warning = remaining < 600 && !urgent && auction.status === "ABERTO";

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const v = parseFloat(bidValue.replace(",", "."));
    if (Number.isNaN(v) || v <= 0) {
      toast.error("Lance inválido");
      return;
    }
    if (v >= auction.bestBid) {
      toast.error(`Lance inválido: valor deve ser menor que ${formatBRL(auction.bestBid)}`);
      return;
    }
    setSubmitting(true);
    try {
      await apiFetch<void>("/v1/bids", {
        method: "POST",
        body: JSON.stringify({
          auctionId: auction.id,
          amount: v,
        }),
      });

      toast.success("Lance enviado! Aguardando confirmação via WebSocket...");
      setBidValue("");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao enviar lance");
    } finally {
      setSubmitting(false);
    }
  };

  const won = auction.status === "ENCERRADO" && auction.winner === user?.name;

  return (
    <div className="space-y-4">
      {/* Header bar */}
      <div className="flex items-center gap-3 rounded-xl border border-border bg-[var(--surface)] px-4 py-3">
        <Link to="/dashboard" className="text-xs text-muted-foreground hover:text-foreground">
          ← Voltar
        </Link>
        <div className="h-4 w-px bg-border" />
        <div className="font-semibold tracking-tight">
          Carga {auction.id} — {auction.cargo.origin.split(" - ")[0]} →{" "}
          {auction.cargo.destination.split(" - ")[0]}
        </div>
        <div className="ml-auto flex items-center gap-3">
          <span
            className={cn(
              "flex h-2 w-2 rounded-full animate-pulse-dot",
              wsStatus === "open"
                ? "bg-success"
                : wsStatus === "connecting"
                  ? "bg-warning"
                  : "bg-destructive",
            )}
          />
          <StatusBadge status={auction.status} />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_1fr_280px]">
        {/* LEFT - cargo details */}
        <aside className="rounded-xl border border-border bg-[var(--surface)] p-4">
          <h3 className="mb-4 flex items-center gap-2 text-sm font-semibold">
            <Package className="h-4 w-4 text-primary" />
            Detalhes da Carga
          </h3>
          <dl className="space-y-3 text-sm">
            <Field
              icon={<MapPin className="h-3.5 w-3.5" />}
              label="Origem"
              value={auction.cargo.origin}
            />
            <Field
              icon={<MapPin className="h-3.5 w-3.5" />}
              label="Destino"
              value={auction.cargo.destination}
            />
            <Field
              icon={<Scale className="h-3.5 w-3.5" />}
              label="Peso"
              value={`${auction.cargo.weight.toLocaleString("pt-BR")} kg`}
            />
            <Field label="Tipo" value={auction.cargo.cargoType} />
            <div>
              <div className="text-xs text-muted-foreground">Descrição</div>
              <div className="mt-1 text-sm leading-relaxed">{auction.cargo.description}</div>
            </div>
            <div className="border-t border-border pt-3">
              <div className="text-xs uppercase tracking-wider text-muted-foreground">
                Valor Inicial (teto)
              </div>
              <div className="font-mono-tnum text-xl font-semibold text-muted-foreground line-through decoration-muted-foreground/40">
                {formatBRL(auction.initialValue)}
              </div>
            </div>
          </dl>

          {/* Route sketch */}
          <div className="mt-4 rounded-md border border-border bg-background p-3">
            <div className="mb-2 text-[10px] uppercase tracking-wider text-muted-foreground">
              Rota
            </div>
            <svg viewBox="0 0 240 50" className="w-full">
              <circle cx="12" cy="25" r="5" fill="#2D7DD2" />
              <line
                x1="20"
                y1="25"
                x2="220"
                y2="25"
                stroke="#2D7DD2"
                strokeWidth="1.5"
                strokeDasharray="4 4"
              />
              <circle cx="228" cy="25" r="5" fill="#16A34A" />
              <rect x="110" y="18" width="20" height="14" rx="2" fill="#161B22" stroke="#2D7DD2" />
            </svg>
            <div className="mt-1 flex justify-between text-[10px] text-muted-foreground">
              <span>{auction.cargo.origin.split(" - ")[0]}</span>
              <span>{auction.cargo.destination.split(" - ")[0]}</span>
            </div>
          </div>
        </aside>

        {/* CENTER - arena */}
        <section className="space-y-4">
          <div
            className={cn(
              "relative rounded-xl border border-border bg-[var(--surface)] p-6 text-center transition-colors",
              urgent && "bg-destructive/5",
            )}
          >
            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
              Menor Lance Atual
            </div>
            <div
              key={flashKey}
              className="mt-2 font-mono-tnum text-5xl font-bold text-foreground animate-flash-glow"
            >
              {formatBRL(auction.bestBid)}
            </div>
            <div className="mt-2 flex items-center justify-center gap-2 text-sm">
              <Crown className="h-4 w-4 text-gold" />
              <span className="text-muted-foreground">Líder:</span>
              <span className="font-medium">{auction.leader ?? "—"}</span>
            </div>

            <div className="mt-6 border-t border-border pt-5">
              <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                {auction.status === "ENCERRADO" ? "Leilão Encerrado" : "Tempo Restante"}
              </div>
              {auction.status === "ENCERRADO" ? (
                <div className="mt-2 inline-block rounded-md bg-destructive/15 px-4 py-2 font-mono-tnum text-2xl font-bold text-destructive">
                  ENCERRADO
                </div>
              ) : (
                <div
                  className={cn(
                    "mt-2 font-mono-tnum text-6xl font-bold tracking-tight",
                    urgent
                      ? "text-destructive animate-pulse-dot"
                      : warning
                        ? "text-warning"
                        : "text-muted-foreground",
                  )}
                >
                  {mm}:{ss}
                </div>
              )}
            </div>
          </div>

          {/* Bid form */}
          {auction.status === "ABERTO" && user?.role === "TRANSPORTADORA" ? (
            <form
              onSubmit={onSubmit}
              className="rounded-xl border border-border bg-[var(--surface)] p-4"
            >
              <label className="text-xs uppercase tracking-wider text-muted-foreground">
                Enviar Lance
              </label>
              <div className="mt-3 flex flex-col gap-3 sm:flex-row">
                <div className="relative flex-1">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                    R$
                  </span>
                  <input
                    type="text"
                    inputMode="decimal"
                    value={bidValue}
                    onChange={(e) => setBidValue(e.target.value)}
                    placeholder={`Menor que ${(auction.bestBid - 1).toFixed(2)}`}
                    className="h-11 w-full rounded-md border border-border bg-background pl-9 pr-3 font-mono-tnum text-base focus:border-primary focus:outline-none"
                  />
                </div>
                <button
                  type="submit"
                  disabled={submitting}
                  className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-primary px-5 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
                >
                  {submitting ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
                  Enviar Lance
                </button>
              </div>
              <div className="mt-2 text-[11px] text-muted-foreground">
                O valor deve ser estritamente menor que o lance atual.
              </div>
            </form>
          ) : auction.status === "ENCERRADO" ? (
            <div
              className={cn(
                "rounded-xl border p-5 animate-slide-in-right",
                won ? "border-success/40 bg-success/10" : "border-border bg-[var(--surface)]",
              )}
            >
              <div className="flex items-center gap-3">
                {won ? (
                  <CheckCircle2 className="h-6 w-6 text-success" />
                ) : (
                  <Trophy className="h-6 w-6 text-muted-foreground" />
                )}
                <div>
                  <div className={cn("text-sm font-semibold", won && "text-success")}>
                    {won ? "Você venceu o leilão!" : `Vencedor: ${auction.winner ?? "—"}`}
                  </div>
                  <div className="font-mono-tnum text-xl font-bold">
                    {formatBRL(auction.bestBid)}
                  </div>
                </div>
              </div>
            </div>
          ) : null}

          {/* Event feed */}
          <div className="rounded-xl border border-border bg-[var(--surface)] p-4">
            <div className="mb-3 text-xs uppercase tracking-wider text-muted-foreground">
              Eventos ao vivo
            </div>
            <div className="max-h-48 overflow-y-auto font-mono-tnum text-xs">
              {recentEvents.length === 0 ? (
                <div className="text-muted-foreground">Aguardando eventos...</div>
              ) : (
                recentEvents.map((e) => (
                  <div
                    key={e.id}
                    className="border-b border-border/40 py-1.5 text-muted-foreground last:border-0 animate-slide-in-right"
                  >
                    <span className="text-[10px] text-muted-foreground/70">
                      [{new Date(e.timestamp).toLocaleTimeString("pt-BR")}]
                    </span>{" "}
                    {e.description}
                  </div>
                ))
              )}
            </div>
          </div>
        </section>

        {/* RIGHT - ranking */}
        <aside className="rounded-xl border border-border bg-[var(--surface)] p-4">
          <h3 className="flex items-center gap-2 text-sm font-semibold">
            <BarChart2 className="h-4 w-4 text-primary" />
            Ranking ao Vivo
          </h3>
          <div className="mt-0.5 text-[11px] text-muted-foreground">atualizado em tempo real</div>

          <div className="mt-4 space-y-2">
            {ranking.length === 0 ? (
              <div className="rounded-md border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
                Nenhum lance ainda
              </div>
            ) : (
              ranking.slice(0, 10).map((b, i) => {
                const isFirst = i === 0;
                const border =
                  i === 0
                    ? "border-l-gold"
                    : i === 1
                      ? "border-l-silver"
                      : i === 2
                        ? "border-l-bronze"
                        : "border-l-border";
                const ago = Math.max(
                  1,
                  Math.floor((Date.now() - new Date(b.timestamp).getTime()) / 1000),
                );
                return (
                  <div
                    key={b.carrier}
                    className={cn(
                      "flex items-center gap-2 rounded-md border border-border border-l-[3px] bg-background p-2.5 text-xs transition-all",
                      border,
                      auction.status === "ENCERRADO" && !isFirst && "opacity-50",
                    )}
                  >
                    <span className="font-mono-tnum w-5 text-muted-foreground">#{i + 1}</span>
                    {isFirst && <Crown className="h-3.5 w-3.5 text-gold" />}
                    <span className={cn("flex-1 truncate", isFirst && "font-semibold")}>
                      {b.carrier}
                    </span>
                    <span className="font-mono-tnum font-bold">{formatBRL(b.value)}</span>
                    {isFirst && auction.status === "ENCERRADO" && (
                      <CheckCircle2 className="h-4 w-4 text-success" />
                    )}
                    {auction.status === "ABERTO" && (
                      <span className="hidden lg:inline text-[10px] text-muted-foreground">
                        há {ago}s
                      </span>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}

function Field({ icon, label, value }: { icon?: React.ReactNode; label: string; value: string }) {
  return (
    <div>
      <div className="flex items-center gap-1 text-xs text-muted-foreground">
        {icon}
        {label}
      </div>
      <div className="mt-0.5 text-sm">{value}</div>
    </div>
  );
}

function Trophy({ className }: { className?: string }) {
  // Re-export to avoid duplicate import name above
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className={className}
    >
      <path
        d="M8 21h8M12 17v4M7 4h10v5a5 5 0 1 1-10 0V4zM7 6H4a2 2 0 0 0 2 4M17 6h3a2 2 0 0 1-2 4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
