import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useState } from "react";
import { Zap, CheckCircle, TrendingDown, Truck, Plus, X, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { AppShell } from "@/components/app-shell";
import { useAuctions } from "@/hooks/useAuctions";
import { useCarriers } from "@/hooks/useCarriers";
import { apiFetch } from "@/lib/api";
import { useStore, formatBRL, type Auction } from "@/lib/store";
import { StatusBadge } from "./dashboard";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/admin")({
  head: () => ({
    meta: [
      { title: "Painel Admin — FreightBid" },
      { name: "description", content: "Gestão de cargas, leilões e transportadoras." },
    ],
  }),
  component: AdminPage,
});

function AdminPage() {
  return (
    <AppShell requireAdmin>
      <AdminContent />
    </AppShell>
  );
}

type Tab = "cargos" | "auctions" | "carriers";

function AdminContent() {
  const { refetch } = useAuctions();
  useCarriers();
  const auctions = useStore((s) => s.auctions);
  const cargos = useStore((s) => s.cargos);
  const carriers = useStore((s) => s.carriers);
  const closeAuction = useStore((s) => s.closeAuction);

  const [tab, setTab] = useState<Tab>("auctions");
  const [drawer, setDrawer] = useState(false);
  const [confirmClose, setConfirmClose] = useState<string | null>(null);
  const [, force] = useState(0);

  useEffect(() => {
    const t = setInterval(() => force((n) => n + 1), 1000);
    return () => clearInterval(t);
  }, []);

  const openCount = auctions.filter((a) => a.status === "ABERTO").length;
  const closedCount = auctions.filter((a) => a.status === "ENCERRADO").length;
  const todayBids = auctions.reduce((acc, a) => acc + a.bids.length, 0);
  const activeCarriers = carriers.filter((c) => c.status === "ATIVA").length;

  const cargoAuctionStatus = (cargoId: string): "SEM LEILÃO" | "COM LEILÃO ATIVO" | "ENCERRADO" => {
    const linked = auctions.filter((a) => a.cargo.id === cargoId);
    if (linked.length === 0) return "SEM LEILÃO";
    if (linked.some((a) => a.status === "ABERTO")) return "COM LEILÃO ATIVO";
    return "ENCERRADO";
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Painel Administrativo</h1>
          <p className="text-sm text-muted-foreground">
            Controle operacional dos leilões em andamento.
          </p>
        </div>
        <button
          onClick={() => setDrawer(true)}
          className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
        >
          <Plus className="h-4 w-4" />
          Novo Leilão
        </button>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <AdminMetric label="Leilões Ativos" value={openCount} icon={Zap} tone="success" />
        <AdminMetric
          label="Leilões Encerrados"
          value={closedCount}
          icon={CheckCircle}
          tone="muted"
        />
        <AdminMetric
          label="Total de Lances Hoje"
          value={todayBids}
          icon={TrendingDown}
          tone="primary"
        />
        <AdminMetric
          label="Transportadoras Ativas"
          value={activeCarriers}
          icon={Truck}
          tone="primary"
        />
      </div>

      <div className="rounded-xl border border-border bg-[var(--surface)]">
        <div className="flex border-b border-border px-2">
          {(["auctions", "cargos", "carriers"] as Tab[]).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={cn(
                "relative px-4 py-3 text-sm transition-colors",
                tab === t ? "text-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {t === "auctions" ? "Leilões" : t === "cargos" ? "Cargas" : "Transportadoras"}
              {tab === t && <span className="absolute bottom-0 left-2 right-2 h-0.5 bg-primary" />}
            </button>
          ))}
        </div>

        {tab === "auctions" && (
          <AuctionsTable auctions={auctions} onClose={(id) => setConfirmClose(id)} />
        )}
        {tab === "cargos" && (
          <CargosTable
            cargos={cargos}
            statusFn={cargoAuctionStatus}
            onCreate={() => setDrawer(true)}
          />
        )}
        {tab === "carriers" && <CarriersTable carriers={carriers} />}
      </div>

      {drawer && (
        <CreateAuctionDrawer
          onClose={() => setDrawer(false)}
          onCreate={async (payload) => {
            const load = await apiFetch<{ id: string }>("/v1/loads", {
              method: "POST",
              body: JSON.stringify({
                origin: payload.origin,
                destination: payload.destination,
                description: `${payload.name} — ${payload.cargoTypes.join(", ")}`,
                weightKg: payload.weightKg,
                initialPrice: payload.initialValue,
              }),
            });

            await apiFetch("/v1/auctions", {
              method: "POST",
              body: JSON.stringify({
                loadId: load.id,
                durationMinutes: payload.durationMin,
              }),
            });

            await refetch();
            toast.success("Leilão criado com sucesso");
            setDrawer(false);
          }}
        />
      )}

      {confirmClose && (
        <ConfirmDialog
          message="Encerrar este leilão? A ação é irreversível."
          onCancel={() => setConfirmClose(null)}
          onConfirm={async () => {
            try {
              await apiFetch(`/v1/auctions/${confirmClose}/close`, {
                method: "PATCH",
              });
              closeAuction(confirmClose);
              await refetch();
              toast.success("Leilão encerrado");
              setConfirmClose(null);
            } catch (error) {
              toast.error(error instanceof Error ? error.message : "Erro ao encerrar leilão");
            }
          }}
        />
      )}
    </div>
  );
}

function AdminMetric({
  label,
  value,
  icon: Icon,
  tone,
}: {
  label: string;
  value: number;
  icon: typeof Zap;
  tone: "primary" | "success" | "muted";
}) {
  const toneCls =
    tone === "success"
      ? "bg-success/15 text-success"
      : tone === "muted"
        ? "bg-muted text-muted-foreground"
        : "bg-primary/15 text-primary";
  return (
    <div className="rounded-xl border border-border bg-[var(--surface)] p-4">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-xs uppercase tracking-wider text-muted-foreground">{label}</div>
          <div className="mt-2 font-mono-tnum text-2xl font-semibold">{value}</div>
        </div>
        <div className={cn("flex h-9 w-9 items-center justify-center rounded-md", toneCls)}>
          <Icon className="h-4 w-4" />
        </div>
      </div>
    </div>
  );
}

function AuctionsTable({
  auctions,
  onClose,
}: {
  auctions: Auction[];
  onClose: (id: string) => void;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
            <Th>ID</Th>
            <Th>Carga</Th>
            <Th className="text-right">Valor Inicial</Th>
            <Th className="text-right">Menor Lance</Th>
            <Th>Vencedor / Líder</Th>
            <Th>Status</Th>
            <Th>Tempo</Th>
            <Th className="text-right">Ações</Th>
          </tr>
        </thead>
        <tbody>
          {auctions.map((a) => {
            const remaining = Math.max(0, Math.floor((a.endsAt - Date.now()) / 1000));
            const mm = String(Math.floor(remaining / 60)).padStart(2, "0");
            const ss = String(remaining % 60).padStart(2, "0");
            return (
              <tr key={a.id} className="border-b border-border/60 hover:bg-[var(--row-hover)]">
                <Td className="font-mono-tnum">{a.id}</Td>
                <Td>
                  <div className="text-sm">{a.cargo.id}</div>
                  <div className="text-xs text-muted-foreground">
                    {a.cargo.origin.split(" - ")[0]} → {a.cargo.destination.split(" - ")[0]}
                  </div>
                </Td>
                <Td className="text-right font-mono-tnum text-muted-foreground">
                  {formatBRL(a.initialValue)}
                </Td>
                <Td className="text-right font-mono-tnum font-semibold text-primary">
                  {formatBRL(a.bestBid)}
                </Td>
                <Td>{a.leader ?? <span className="text-muted-foreground">—</span>}</Td>
                <Td>
                  <StatusBadge status={a.status} />
                </Td>
                <Td className="font-mono-tnum">{a.status === "ABERTO" ? `${mm}:${ss}` : "—"}</Td>
                <Td>
                  <div className="flex items-center justify-end gap-2">
                    {a.status === "ABERTO" && (
                      <button
                        onClick={() => onClose(a.id)}
                        className="rounded-md border border-destructive/30 px-2.5 py-1 text-xs text-destructive transition-colors hover:bg-destructive/10"
                      >
                        Encerrar
                      </button>
                    )}
                    <Link
                      to="/auction/$id/history"
                      params={{ id: a.id }}
                      className="rounded-md border border-border px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:bg-[var(--row-hover)] hover:text-foreground"
                    >
                      Histórico
                    </Link>
                  </div>
                </Td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function CargosTable({
  cargos,
  statusFn,
  onCreate,
}: {
  cargos: ReturnType<typeof useStore.getState>["cargos"];
  statusFn: (id: string) => "SEM LEILÃO" | "COM LEILÃO ATIVO" | "ENCERRADO";
  onCreate: () => void;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
            <Th>ID</Th>
            <Th>Descrição</Th>
            <Th>Origem</Th>
            <Th>Destino</Th>
            <Th className="text-right">Peso (kg)</Th>
            <Th>Status</Th>
            <Th className="text-right">Ações</Th>
          </tr>
        </thead>
        <tbody>
          {cargos.map((c) => {
            const status = statusFn(c.id);
            const badgeColor =
              status === "COM LEILÃO ATIVO"
                ? "bg-success/15 text-success"
                : status === "ENCERRADO"
                  ? "bg-muted text-muted-foreground"
                  : "bg-warning/15 text-warning";
            return (
              <tr key={c.id} className="border-b border-border/60 hover:bg-[var(--row-hover)]">
                <Td className="font-mono-tnum">{c.id}</Td>
                <Td className="max-w-[280px] truncate">{c.description}</Td>
                <Td>{c.origin}</Td>
                <Td>{c.destination}</Td>
                <Td className="text-right font-mono-tnum">{c.weight.toLocaleString("pt-BR")}</Td>
                <Td>
                  <span
                    className={cn("rounded-full px-2 py-0.5 text-[11px] font-medium", badgeColor)}
                  >
                    {status}
                  </span>
                </Td>
                <Td className="text-right">
                  <button
                    onClick={onCreate}
                    className="rounded-md bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary hover:bg-primary/20"
                  >
                    Criar Leilão
                  </button>
                </Td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function CarriersTable({
  carriers,
}: {
  carriers: ReturnType<typeof useStore.getState>["carriers"];
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-xs uppercase tracking-wider text-muted-foreground">
            <Th>ID</Th>
            <Th>Nome</Th>
            <Th>Email</Th>
            <Th className="text-right">Lances Enviados</Th>
            <Th className="text-right">Taxa de Vitória</Th>
            <Th>Status</Th>
            <Th>Cadastro</Th>
          </tr>
        </thead>
        <tbody>
          {carriers.map((c) => (
            <tr key={c.id} className="border-b border-border/60 hover:bg-[var(--row-hover)]">
              <Td className="font-mono-tnum">{c.id}</Td>
              <Td className="font-medium">{c.name}</Td>
              <Td className="text-muted-foreground">{c.email}</Td>
              <Td className="text-right font-mono-tnum">{c.bidsSent}</Td>
              <Td className="text-right font-mono-tnum text-success">{c.winRate}%</Td>
              <Td>
                <span
                  className={cn(
                    "rounded-full px-2 py-0.5 text-[11px] font-medium",
                    c.status === "ATIVA"
                      ? "bg-success/15 text-success"
                      : "bg-muted text-muted-foreground",
                  )}
                >
                  {c.status}
                </span>
              </Td>
              <Td className="text-muted-foreground">
                {new Date(c.createdAt).toLocaleDateString("pt-BR")}
              </Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const CARGO_TYPES = [
  "Frágil",
  "Líquido",
  "Inflamável",
  "Perecível",
  "Eletrônicos",
  "Carga Seca",
  "Granel",
  "Refrigerado",
  "Químico",
  "Maquinário Pesado",
  "Outro",
];

type CreatePayload = {
  name: string;
  origin: string;
  destination: string;
  cargoTypes: string[];
  weightKg: number;
  initialValue: number;
  durationMin: number;
};

function CreateAuctionDrawer({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: (payload: CreatePayload) => void;
}) {
  const [name, setName] = useState("");
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [types, setTypes] = useState<string[]>([]);
  const [weightKg, setWeightKg] = useState("1000");
  const [value, setValue] = useState("10000");
  const [dur, setDur] = useState<number>(30);
  const [custom, setCustom] = useState(false);
  const [customDur, setCustomDur] = useState("45");
  const [loading, setLoading] = useState(false);

  const toggleType = (t: string) =>
    setTypes((arr) => (arr.includes(t) ? arr.filter((x) => x !== t) : [...arr, t]));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const v = parseFloat(value);
    const weight = parseFloat(weightKg);
    const finalDur = custom ? parseInt(customDur, 10) : dur;
    if (!name.trim() || !origin.trim() || !destination.trim()) {
      toast.error("Preencha nome, origem e destino");
      return;
    }
    if (types.length === 0) {
      toast.error("Selecione ao menos um tipo de carga");
      return;
    }
    if (Number.isNaN(v) || v <= 0) {
      toast.error("Valor inicial inválido");
      return;
    }
    if (Number.isNaN(weight) || weight <= 0) {
      toast.error("Peso inválido");
      return;
    }
    if (!Number.isFinite(finalDur) || finalDur <= 0) {
      toast.error("Duração inválida");
      return;
    }
    setLoading(true);
    try {
      await onCreate({
        name: name.trim(),
        origin: origin.trim(),
        destination: destination.trim(),
        cargoTypes: types,
        weightKg: weight,
        initialValue: v,
        durationMin: finalDur,
      });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao criar leilão");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <aside className="w-full max-w-[480px] overflow-y-auto border-l border-border bg-[var(--surface)] p-6 animate-slide-in-right">
        <div className="mb-6 flex items-center justify-between">
          <h3 className="text-lg font-semibold">Novo Leilão</h3>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={submit} className="space-y-5">
          <div>
            <label className="text-xs font-medium text-muted-foreground">Nome do Leilão</label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex.: Frete SP → Recife - Junho/26"
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
            />
          </div>

          <div>
            <label className="text-xs font-medium text-muted-foreground">Local de Partida</label>
            <input
              value={origin}
              onChange={(e) => setOrigin(e.target.value)}
              placeholder="Cidade - UF"
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
            />
          </div>

          <div>
            <label className="text-xs font-medium text-muted-foreground">Local de Destino</label>
            <input
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
              placeholder="Cidade - UF"
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
            />
          </div>

          <div>
            <label className="text-xs font-medium text-muted-foreground">
              Tipo de Carga <span className="text-destructive">*</span>
            </label>
            <div className="mt-1 rounded-md border border-border bg-background p-2">
              {types.length > 0 && (
                <div className="mb-2 flex flex-wrap gap-1.5">
                  {types.map((t) => (
                    <span
                      key={t}
                      className="inline-flex items-center gap-1 rounded-full bg-primary/15 px-2 py-0.5 text-xs font-medium text-primary"
                    >
                      {t}
                      <button
                        type="button"
                        onClick={() => toggleType(t)}
                        className="text-primary/70 hover:text-primary"
                        aria-label={`Remover ${t}`}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
              <div className="flex flex-wrap gap-1.5">
                {CARGO_TYPES.filter((t) => !types.includes(t)).map((t) => (
                  <button
                    type="button"
                    key={t}
                    onClick={() => toggleType(t)}
                    className="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground transition-colors hover:border-primary/40 hover:bg-primary/10 hover:text-primary"
                  >
                    + {t}
                  </button>
                ))}
                {CARGO_TYPES.every((t) => types.includes(t)) && (
                  <span className="text-xs text-muted-foreground">
                    Todos os tipos selecionados.
                  </span>
                )}
              </div>
            </div>
          </div>

          <div>
            <label className="text-xs font-medium text-muted-foreground">Valor Inicial (R$)</label>
            <input
              type="number"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 font-mono-tnum text-sm focus:border-primary focus:outline-none"
            />
          </div>

          <div>
            <label className="text-xs font-medium text-muted-foreground">Peso da Carga (kg)</label>
            <input
              type="number"
              min={0.01}
              step={0.01}
              value={weightKg}
              onChange={(e) => setWeightKg(e.target.value)}
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 font-mono-tnum text-sm focus:border-primary focus:outline-none"
            />
          </div>

          <div>
            <label className="text-xs font-medium text-muted-foreground">Duração (minutos)</label>
            <div className="mt-1 grid grid-cols-5 gap-2">
              {[15, 30, 60, 120].map((m) => (
                <button
                  type="button"
                  key={m}
                  onClick={() => {
                    setDur(m);
                    setCustom(false);
                  }}
                  className={cn(
                    "h-10 rounded-md border text-sm font-medium transition-colors",
                    !custom && dur === m
                      ? "border-primary bg-primary/15 text-primary"
                      : "border-border text-muted-foreground hover:bg-[var(--row-hover)]",
                  )}
                >
                  {m}
                </button>
              ))}
              <button
                type="button"
                onClick={() => setCustom(true)}
                className={cn(
                  "h-10 rounded-md border text-sm font-medium transition-colors",
                  custom
                    ? "border-primary bg-primary/15 text-primary"
                    : "border-border text-muted-foreground hover:bg-[var(--row-hover)]",
                )}
              >
                Custom
              </button>
            </div>
            {custom && (
              <input
                type="number"
                min={1}
                value={customDur}
                onChange={(e) => setCustomDur(e.target.value)}
                placeholder="Duração personalizada em minutos"
                className="mt-2 h-10 w-full rounded-md border border-border bg-background px-3 font-mono-tnum text-sm focus:border-primary focus:outline-none"
              />
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="flex h-10 w-full items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
          >
            {loading && <Loader2 className="h-4 w-4 animate-spin" />}
            Criar Leilão
          </button>
        </form>
      </aside>
    </div>
  );
}

function ConfirmDialog({
  message,
  onCancel,
  onConfirm,
}: {
  message: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-xl border border-border bg-[var(--elevated)] p-6">
        <h3 className="text-base font-semibold">Confirmar ação</h3>
        <p className="mt-2 text-sm text-muted-foreground">{message}</p>
        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={onCancel}
            className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-[var(--row-hover)]"
          >
            Cancelar
          </button>
          <button
            onClick={onConfirm}
            className="rounded-md bg-destructive px-3 py-1.5 text-sm font-medium text-destructive-foreground hover:bg-destructive/90"
          >
            Confirmar
          </button>
        </div>
      </div>
    </div>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={cn("p-3 text-left font-medium", className)}>{children}</th>;
}
function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={cn("p-3 align-middle", className)}>{children}</td>;
}
// satisfy memo
void useMemo;
