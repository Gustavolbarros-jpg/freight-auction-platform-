import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect, useState, type ReactNode } from "react";
import {
  LayoutDashboard,
  Settings,
  LogOut,
  Truck,
  Search,
  Bell,
  ChevronLeft,
  ChevronRight,
  BarChart3,
} from "lucide-react";
import { useStore } from "@/lib/store";
import { useGlobalTick } from "@/lib/use-websocket";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  adminOnly?: boolean;
}

const NAV: NavItem[] = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/admin", label: "Painel Admin", icon: BarChart3, adminOnly: true },
  { to: "/settings", label: "Configurações", icon: Settings },
];

export function AppShell({ children, requireAdmin = false }: { children: ReactNode; requireAdmin?: boolean }) {
  const user = useStore((s) => s.user);
  const token = useStore((s) => s.token);
  const logout = useStore((s) => s.logout);
  const wsStatus = useStore((s) => s.wsStatus);
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  useGlobalTick();

  useEffect(() => {
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    if (!token || !user) {
      navigate({ to: "/login" });
      return;
    }
    if (requireAdmin && user.role !== "ADMIN") {
      navigate({ to: "/dashboard" });
    }
  }, [hydrated, token, user, requireAdmin, navigate]);

  const pathname = useRouterState({ select: (s) => s.location.pathname });

  if (!hydrated || !token || !user) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="text-muted-foreground text-sm">Carregando...</div>
      </div>
    );
  }

  const visibleNav = NAV.filter((n) => !n.adminOnly || user.role === "ADMIN");
  const wsColor =
    wsStatus === "open" ? "bg-success" : wsStatus === "connecting" ? "bg-warning" : "bg-destructive";
  const wsLabel =
    wsStatus === "open" ? "Conectado" : wsStatus === "connecting" ? "Conectando..." : "Desconectado";

  const breadcrumb = pathname.split("/").filter(Boolean).join(" / ") || "home";

  return (
    <div className="flex min-h-screen w-full bg-background text-foreground">
      <aside
        className={cn(
          "flex flex-col border-r border-border bg-[var(--sidebar)] transition-[width] duration-200",
          collapsed ? "w-16" : "w-60",
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b border-border px-4">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/15 text-primary">
            <Truck className="h-4 w-4" />
          </div>
          {!collapsed && (
            <span className="font-semibold tracking-tight">FreightBid</span>
          )}
          <button
            onClick={() => setCollapsed((c) => !c)}
            className="ml-auto text-muted-foreground hover:text-foreground"
            aria-label="Toggle sidebar"
          >
            {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </button>
        </div>
        <nav className="flex-1 space-y-0.5 p-2">
          {visibleNav.map((item, i) => {
            const active = pathname === item.to || (item.to === "/admin" && pathname.startsWith("/admin"));
            const Icon = item.icon;
            return (
              <Link
                key={i}
                to={item.to}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                  active
                    ? "bg-[var(--row-hover)] text-foreground"
                    : "text-muted-foreground hover:bg-[var(--row-hover)] hover:text-foreground",
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                {!collapsed && <span className="truncate">{item.label}</span>}
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-border p-3 space-y-3">
          <div className="flex items-center gap-2 text-xs">
            <span className={cn("h-2 w-2 rounded-full animate-pulse-dot", wsColor)} />
            {!collapsed && <span className="text-muted-foreground">{wsLabel}</span>}
          </div>
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/20 text-xs font-semibold text-primary">
              {user.name.slice(0, 2).toUpperCase()}
            </div>
            {!collapsed && (
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{user.name}</div>
                <span
                  className={cn(
                    "inline-block rounded px-1.5 py-0.5 text-[10px] font-medium tracking-wide",
                    user.role === "ADMIN"
                      ? "bg-accent/20 text-accent-foreground"
                      : "bg-primary/15 text-primary",
                  )}
                >
                  {user.role}
                </span>
              </div>
            )}
            <button
              onClick={() => {
                logout();
                navigate({ to: "/login" });
              }}
              className="text-muted-foreground hover:text-destructive"
              aria-label="Sair"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center gap-4 border-b border-border bg-[var(--surface)] px-6">
          <div className="text-xs uppercase tracking-wider text-muted-foreground">
            {breadcrumb}
          </div>
          <div className="ml-auto flex items-center gap-3">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <input
                type="text"
                placeholder="Buscar..."
                className="h-8 w-64 rounded-md border border-border bg-background pl-8 pr-3 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none"
              />
            </div>
            <button className="relative text-muted-foreground hover:text-foreground">
              <Bell className="h-4 w-4" />
              <span className="absolute -right-1 -top-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-destructive text-[9px] font-bold text-white">
                3
              </span>
            </button>
          </div>
        </header>
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  );
}
