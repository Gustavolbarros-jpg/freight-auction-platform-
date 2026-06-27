import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Eye, EyeOff, Truck, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useStore } from "@/lib/store";

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: "Entrar — FreightBid" },
      { name: "description", content: "Acesse a plataforma de leilão de fretes." },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const navigate = useNavigate();
  const login = useStore((s) => s.login);
  const existing = useStore((s) => s.user);
  const [email, setEmail] = useState("transportadora@freight.com.br");
  const [password, setPassword] = useState("demo123");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (existing) {
      navigate({ to: existing.role === "ADMIN" ? "/admin" : "/dashboard" });
    }
  }, [existing, navigate]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      toast.error("Preencha e-mail e senha");
      return;
    }
    setLoading(true);
    try {
      const user = await login(email, password);
      toast.success(`Bem-vindo, ${user.name}`);
      navigate({ to: user.role === "ADMIN" ? "/admin" : "/dashboard" });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Falha ao fazer login");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[3fr_2fr] bg-background text-foreground">
      {/* Left visual panel */}
      <div className="relative hidden overflow-hidden lg:flex flex-col justify-between p-12 bg-gradient-to-br from-[#0A0D12] via-[#0d1320] to-[#0a1a2e]">
        <div className="flex items-center gap-3 text-foreground">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/20 text-primary">
            <Truck className="h-5 w-5" />
          </div>
          <span className="text-lg font-semibold tracking-tight">FreightBid</span>
        </div>

        {/* Animated SVG network */}
        <svg
          viewBox="0 0 600 400"
          className="absolute inset-0 m-auto h-full w-full opacity-60"
          preserveAspectRatio="xMidYMid meet"
        >
          <defs>
            <linearGradient id="line" x1="0" x2="1">
              <stop offset="0%" stopColor="#2D7DD2" stopOpacity="0.1" />
              <stop offset="50%" stopColor="#2D7DD2" stopOpacity="0.7" />
              <stop offset="100%" stopColor="#2D7DD2" stopOpacity="0.1" />
            </linearGradient>
          </defs>
          {[
            ["80,80", "300,160"],
            ["300,160", "520,90"],
            ["300,160", "180,290"],
            ["300,160", "470,310"],
            ["180,290", "470,310"],
            ["80,80", "180,290"],
            ["520,90", "470,310"],
          ].map(([a, b], i) => {
            const [x1, y1] = a.split(",").map(Number);
            const [x2, y2] = b.split(",").map(Number);
            return (
              <g key={i}>
                <line
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke="url(#line)"
                  strokeWidth="1"
                  strokeDasharray="4 6"
                >
                  <animate
                    attributeName="stroke-dashoffset"
                    from="0"
                    to="-20"
                    dur={`${3 + i * 0.4}s`}
                    repeatCount="indefinite"
                  />
                </line>
              </g>
            );
          })}
          {[
            [80, 80],
            [300, 160],
            [520, 90],
            [180, 290],
            [470, 310],
          ].map(([x, y], i) => (
            <g key={i}>
              <circle cx={x} cy={y} r="6" fill="#2D7DD2" />
              <circle cx={x} cy={y} r="14" fill="none" stroke="#2D7DD2" strokeOpacity="0.4">
                <animate
                  attributeName="r"
                  from="6"
                  to="22"
                  dur="2.4s"
                  repeatCount="indefinite"
                  begin={`${i * 0.4}s`}
                />
                <animate
                  attributeName="stroke-opacity"
                  from="0.5"
                  to="0"
                  dur="2.4s"
                  repeatCount="indefinite"
                  begin={`${i * 0.4}s`}
                />
              </circle>
            </g>
          ))}
        </svg>

        <div className="relative z-10 space-y-3">
          <h1 className="font-mono-tnum text-5xl font-bold tracking-tight">FreightBid</h1>
          <p className="max-w-md text-lg text-muted-foreground">
            Leilões de frete em tempo real. Transportadoras competem, embarcadores economizam.
          </p>
          <div className="flex gap-6 pt-6 text-sm text-muted-foreground">
            <div>
              <div className="font-mono-tnum text-2xl font-semibold text-foreground">R$ 1,2M</div>
              <div>Economia gerada</div>
            </div>
            <div>
              <div className="font-mono-tnum text-2xl font-semibold text-foreground">847</div>
              <div>Leilões realizados</div>
            </div>
            <div>
              <div className="font-mono-tnum text-2xl font-semibold text-foreground">38%</div>
              <div>Redução média</div>
            </div>
          </div>
        </div>
      </div>

      {/* Right form */}
      <div className="flex items-center justify-center p-8">
        <div className="w-full max-w-sm rounded-xl border border-border bg-[var(--surface)] p-6 shadow-2xl">
          <div className="mb-6 lg:hidden flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/20 text-primary">
              <Truck className="h-4 w-4" />
            </div>
            <span className="font-semibold">FreightBid</span>
          </div>
          <h2 className="text-xl font-semibold tracking-tight">Acessar plataforma</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Use um email com "admin" para acesso administrativo.
          </p>

          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">E-mail</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="sua@empresa.com.br"
                className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground">Senha</label>
              <div className="relative">
                <input
                  type={showPw ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="h-10 w-full rounded-md border border-border bg-background px-3 pr-10 text-sm focus:border-primary focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => setShowPw((s) => !s)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="flex h-10 w-full items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-60"
            >
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              Entrar
            </button>
          </form>

          <div className="mt-5 text-center text-xs text-muted-foreground">
            Plataforma B2B — acessos provisionados pelo embarcador.
          </div>
        </div>
      </div>
    </div>
  );
}
