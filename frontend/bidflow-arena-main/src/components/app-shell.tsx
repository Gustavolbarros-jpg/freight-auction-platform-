import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect, useState, type ReactNode } from "react";
import {
  LayoutDashboard,
  Settings,
  LogOut,
  Truck,
  Bell,
  ChevronLeft,
  ChevronRight,
  BarChart3,
} from "lucide-react";
import { useStore } from "@/lib/store";
import { useGlobalNotifications, useGlobalTick } from "@/lib/use-websocket";
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
  const notifications = useStore((s) => s.notifications);
  const markNotificationsRead = useStore((s) => s.markNotificationsRead);
  const clearNotifications = useStore((s) => s.clearNotifications);
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [hydrated, setHydrated] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  useGlobalTick();
  useGlobalNotifications();

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
  const unreadCount = notifications.filter((notification) => !notification.read).length;

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
              <button
                onClick={() => {
                  const nextOpen = !notificationsOpen;
                  setNotificationsOpen(nextOpen);
                  if (nextOpen) markNotificationsRead();
                }}
                className="relative text-muted-foreground hover:text-foreground"
                aria-label="Notificações"
              >
                <Bell className="h-4 w-4" />
                {unreadCount > 0 && (
                  <span className="absolute -right-1 -top-1 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-destructive px-1 text-[9px] font-bold text-white">
                    {unreadCount > 9 ? "9+" : unreadCount}
                  </span>
                )}
              </button>

              {notificationsOpen && (
                <div className="absolute right-0 top-8 z-50 w-96 overflow-hidden rounded-xl border border-border bg-[var(--surface)] shadow-2xl">
                  <div className="flex items-center justify-between border-b border-border px-4 py-3">
                    <div>
                      <div className="text-sm font-semibold">Notificações</div>
                      <div className="text-[11px] text-muted-foreground">
                        Últimos eventos da plataforma
                      </div>
                    </div>
                    {notifications.length > 0 && (
                      <button
                        type="button"
                        onClick={clearNotifications}
                        className="rounded-md px-2 py-1 text-[11px] text-muted-foreground hover:bg-background hover:text-foreground"
                      >
                        Limpar
                      </button>
                    )}
                  </div>

                  <div className="max-h-96 overflow-y-auto">
                    {notifications.length === 0 ? (
                      <div className="px-4 py-8 text-center text-sm text-muted-foreground">
                        Nenhuma notificação ainda.
                      </div>
                    ) : (
                      notifications.map((notification) => (
                        <div
                          key={notification.id}
                          className="border-b border-border/50 px-4 py-3 last:border-0"
                        >
                          <div className="flex items-start gap-3">
                            <span
                              className={cn(
                                "mt-1 h-2 w-2 shrink-0 rounded-full",
                                notification.kind === "BID" ||
                                  notification.kind === "AUCTION_OPENED"
                                  ? "bg-primary"
                                  : notification.kind === "CLOSING_SOON"
                                    ? "bg-warning"
                                    : notification.kind === "AUCTION_CLOSED"
                                      ? "bg-success"
                                      : "bg-muted-foreground",
                              )}
                            />
                            <div className="min-w-0 flex-1">
                              <div className="text-sm font-medium">{notification.title}</div>
                              <div className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                                {notification.description}
                              </div>
                              <div className="mt-1 text-[10px] text-muted-foreground/70">
                                {new Date(notification.timestamp).toLocaleString("pt-BR")}
                              </div>
                            </div>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  );
}
