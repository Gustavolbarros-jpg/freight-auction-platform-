import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Activity, TrendingDown, Trophy, DollarSign, ArrowRight } from "lucide-react";
import { LineChart, Line, ResponsiveContainer, Tooltip as RTooltip } from "recharts";
import { AppShell } from "@/components/app-shell";
import { useAuctions } from "@/hooks/useAuctions";
import { useStore, formatBRL, type Auction } from "@/lib/store";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/dashboard")({
  head: () => ({
    meta: [
      { title: "Dashboard — FreightBid" },
      { name: "description", content: "Leilões abertos e métricas em tempo real." },
    ],
  }),
  component: Dashboard,
});

function Dashboard() {
  return (
    <AppShell>
      <DashboardContent />
    </AppShell>
  );
}

function DashboardContent() {
  const user = useStore((s) => s.user);
  const localAuctions = useStore((s) => s.auctions);
  const { data: remoteAuctions, isLoading, error } = useAuctions();
  const auctions = remoteAuctions ?? localAuctions;
  const [tab, setTab] = useState<"open" | "history">("open");
  const [, force] = useState(0);
  const isAdmin = user?.role === "ADMIN";
  const userName = user?.name ?? "";

  useEffect(() => {
    const t = setInterval(() => force((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, []);

  const openCount = auctions.filter((a) => getDisplayAuctionStatus(a) === "ABERTO").length;
  const sentBids = isAdmin
    ? auctions.reduce((acc, a) => acc + a.bids.length, 0)
    : auctions.reduce((acc, a) => acc + a.bids.filter((bid) => bid.carrier === userName).length, 0);
  const userClosedAuctions = auctions.filter(
    (a) => getDisplayAuctionStatus(a) === "ENCERRADO" && auctionHasCarrier(a, userName),
  );
  const userWins = userClosedAuctions.filter((a) => a.winner === userName).length;
  const winRate =
    userClosedAuctions.length > 0 ? Math.round((userWins / userClosedAuctions.length) * 100) : 0;
  const savings = auctions
    .filter((a) => getDisplayAuctionStatus(a) === "ENCERRADO")
    .reduce((acc, a) => acc + (a.initialValue - a.bestBid), 0);
  const topCarrier = getTopCarrierThisMonth(auctions);
  const carrierMonthlyTotal = auctions
    .filter(
      (a) =>
        getDisplayAuctionStatus(a) === "ENCERRADO" &&
        a.winner === userName &&
        isCurrentMonth(a.endsAt),
    )
    .reduce((acc, a) => acc + a.bestBid, 0);

  const spark = Array.from({ length: 14 }).map((_, i) => ({
    v: 20 + Math.round(Math.sin(i / 2) * 12 + Math.random() * 10),
  }));

  const visible =
    tab === "open"
      ? auctions.filter((a) => getDisplayAuctionStatus(a) === "ABERTO")
      : auctions.filter(
          (a) =>
            getDisplayAuctionStatus(a) === "ENCERRADO" &&
            (isAdmin || auctionHasCarrier(a, userName)),
        );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          {user?.role === "ADMIN" ? "Dashboard de Leilões" : "Painel da Transportadora"}
        </h1>
        <p className="text-sm text-muted-foreground">
          {user?.role === "ADMIN"
            ? "Visualize os leilões e acompanhe a disputa em tempo real."
            : "Acompanhe leilões abertos e participe em tempo real."}
        </p>
      </div>

      {isLoading && (
        <div className="rounded-lg border border-border bg-[var(--surface)] px-4 py-3 text-sm text-muted-foreground">
          Carregando leilões reais...
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Não foi possível carregar os leilões reais:{" "}
          {error instanceof Error ? error.message : "erro desconhecido"}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Leilões Ativos"
          value={String(openCount)}
          icon={Activity}
          tone="primary"
        />
        <MetricCard
          label={isAdmin ? "Total de Lances" : "Meus Lances"}
          value={String(sentBids)}
          icon={TrendingDown}
          tone="primary"
          chart={
            <ResponsiveContainer width="100%" height={36}>
              <LineChart data={spark}>
                <Line dataKey="v" stroke="#2D7DD2" strokeWidth={2} dot={false} />
                <RTooltip contentStyle={{ display: "none" }} />
              </LineChart>
            </ResponsiveContainer>
          }
        />
        {isAdmin ? (
          <MetricCard
            label="Top Transportadora/Mês"
            value={topCarrier?.name ?? "—"}
            icon={Trophy}
            tone="success"
            delta={topCarrier ? `${topCarrier.wins} vitória(s) no mês` : "Sem vitórias no mês"}
          />
        ) : (
          <MetricCard
            label="Taxa de Vitórias"
            value={`${winRate}%`}
            icon={Trophy}
            tone="success"
            chart={
              <div className="relative h-9 w-9">
                <svg viewBox="0 0 36 36" className="h-9 w-9 -rotate-90">
                  <circle
                    cx="18"
                    cy="18"
                    r="14"
                    stroke="rgba(255,255,255,0.08)"
                    strokeWidth="3"
                    fill="none"
                  />
                  <circle
                    cx="18"
                    cy="18"
                    r="14"
                    stroke="#16A34A"
                    strokeWidth="3"
                    fill="none"
                    strokeDasharray={`${(winRate / 100) * 88} 88`}
                    strokeLinecap="round"
                  />
                </svg>
              </div>
            }
          />
        )}
        {isAdmin ? (
          <MetricCard
            label="Economia Gerada"
            value={formatBRL(savings)}
            icon={DollarSign}
            tone="success"
            delta="+12,4% vs mês passado"
          />
        ) : (
          <MetricCard
            label="Total Gasto no Mês"
            value={formatBRL(carrierMonthlyTotal)}
            icon={DollarSign}
            tone="success"
            delta="Soma dos leilões vencidos"
          />
        )}
      </div>

      <div className="rounded-xl border border-border bg-[var(--surface)]">
        <div className="flex items-center gap-1 border-b border-border px-2">
          <TabBtn active={tab === "open"} onClick={() => setTab("open")}>
            Leilões Abertos
          </TabBtn>
          <TabBtn active={tab === "history"} onClick={() => setTab("history")}>
            Meu Histórico
          </TabBtn>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
                <Th>Origem</Th>
                <Th>Destino</Th>
                <Th className="text-right">Peso (kg)</Th>
                <Th className="text-right">Valor Inicial</Th>
                <Th className="text-right">Menor Lance</Th>
                <Th>Tempo Restante</Th>
                <Th>Status</Th>
                <Th className="text-right">Ações</Th>
              </tr>
            </thead>
            <tbody>
              {visible.length === 0 ? (
                <tr>
                  <td colSpan={8} className="p-12 text-center text-muted-foreground">
                    Nenhum leilão {tab === "open" ? "aberto" : "no histórico"} no momento.
                  </td>
                </tr>
              ) : (
                visible.map((a) => {
                  const remaining = Math.max(0, Math.floor((a.endsAt - Date.now()) / 1000));
                  const mm = String(Math.floor(remaining / 60)).padStart(2, "0");
                  const ss = String(remaining % 60).padStart(2, "0");
                  const displayStatus = getDisplayAuctionStatus(a);
                  const ending = remaining > 0 && remaining < 300 && displayStatus === "ABERTO";
                  const primaryAction = user?.role !== "ADMIN" && displayStatus === "ABERTO";
                  const actionLabel =
                    user?.role === "ADMIN"
                      ? "Visualizar"
                      : displayStatus === "ABERTO"
                        ? "Participar"
                        : "Ver detalhes";
                  return (
                    <tr
                      key={a.id}
                      className="border-b border-border/60 transition-colors hover:bg-[var(--row-hover)]"
                    >
                      <Td>{a.cargo.origin}</Td>
                      <Td>{a.cargo.destination}</Td>
                      <Td className="text-right font-mono-tnum">
                        {a.cargo.weight.toLocaleString("pt-BR")}
                      </Td>
                      <Td className="text-right font-mono-tnum text-muted-foreground">
                        {formatBRL(a.initialValue)}
                      </Td>
                      <Td className="text-right font-mono-tnum font-semibold text-primary">
                        {formatBRL(a.bestBid)}
                      </Td>
                      <Td>
                        {displayStatus === "ENCERRADO" ? (
                          <span className="text-muted-foreground">—</span>
                        ) : (
                          <span
                            className={cn(
                              "font-mono-tnum",
                              ending ? "text-destructive" : "text-muted-foreground",
                            )}
                          >
                            {mm}:{ss}
                          </span>
                        )}
                      </Td>
                      <Td>
                        <StatusBadge status={ending ? "ENCERRANDO" : displayStatus} />
                      </Td>
                      <Td className="text-right">
                        <Link
                          to="/auction/$id"
                          params={{ id: a.id }}
                          className={cn(
                            "inline-flex items-center gap-1 rounded-md px-3 py-1.5 text-xs font-medium transition-colors",
                            primaryAction
                              ? "bg-primary text-primary-foreground hover:bg-primary/90"
                              : "border border-border text-muted-foreground hover:bg-[var(--row-hover)]",
                          )}
                        >
                          {actionLabel}
                          <ArrowRight className="h-3 w-3" />
                        </Link>
                      </Td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function getDisplayAuctionStatus(auction: { status: "ABERTO" | "ENCERRADO"; endsAt: number }) {
  if (auction.status === "ABERTO" && auction.endsAt <= Date.now()) {
    return "ENCERRADO";
  }

  return auction.status;
}

function auctionHasCarrier(auction: Auction, carrierName: string) {
  if (!carrierName) return false;
  return auction.winner === carrierName || auction.bids.some((bid) => bid.carrier === carrierName);
}

function isCurrentMonth(timestamp: number) {
  const date = new Date(timestamp);
  const now = new Date();
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth();
}

function getTopCarrierThisMonth(auctions: Auction[]) {
  const wins = new Map<string, number>();

  for (const auction of auctions) {
    if (
      getDisplayAuctionStatus(auction) !== "ENCERRADO" ||
      !auction.winner ||
      !isCurrentMonth(auction.endsAt)
    ) {
      continue;
    }

    wins.set(auction.winner, (wins.get(auction.winner) ?? 0) + 1);
  }

  const [name, count] =
    [...wins.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0] ?? [];

  return name ? { name, wins: count } : null;
}

function MetricCard({
  label,
  value,
  icon: Icon,
  tone,
  chart,
  delta,
}: {
  label: string;
  value: string;
  icon: typeof Activity;
  tone: "primary" | "success";
  chart?: React.ReactNode;
  delta?: string;
}) {
  return (
    <div className="rounded-xl border border-border bg-[var(--surface)] p-4">
      <div className="flex items-start justify-between">
        <div>
          <div className="text-xs uppercase tracking-wider text-muted-foreground">{label}</div>
          <div className="mt-2 font-mono-tnum text-2xl font-semibold">{value}</div>
          {delta && <div className="mt-1 text-xs text-success">{delta}</div>}
        </div>
        <div
          className={cn(
            "flex h-9 w-9 items-center justify-center rounded-md",
            tone === "primary" ? "bg-primary/15 text-primary" : "bg-success/15 text-success",
          )}
        >
          <Icon className="h-4 w-4" />
        </div>
      </div>
      {chart && <div className="mt-3">{chart}</div>}
    </div>
  );
}

function TabBtn({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "relative px-4 py-3 text-sm transition-colors",
        active ? "text-foreground" : "text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
      {active && (
        <span className="absolute bottom-0 left-2 right-2 h-0.5 rounded-full bg-primary" />
      )}
    </button>
  );
}

export function StatusBadge({ status }: { status: "ABERTO" | "ENCERRADO" | "ENCERRANDO" }) {
  const map = {
    ABERTO: "bg-success/15 text-success border-success/30",
    ENCERRANDO: "bg-warning/15 text-warning border-warning/30",
    ENCERRADO: "bg-muted text-muted-foreground border-border",
  } as const;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-medium",
        map[status],
      )}
    >
      <span
        className={cn(
          "h-1.5 w-1.5 rounded-full",
          status === "ABERTO"
            ? "bg-success animate-pulse-dot"
            : status === "ENCERRANDO"
              ? "bg-warning animate-pulse-dot"
              : "bg-muted-foreground",
        )}
      />
      {status}
    </span>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn("p-3 text-left font-medium", className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn("p-3 align-middle", className)}>{children}</td>;
}
