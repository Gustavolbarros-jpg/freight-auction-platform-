import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { User as UserIcon, Bell } from "lucide-react";
import { AppShell } from "@/components/app-shell";
import { useStore } from "@/lib/store";
import { Switch } from "@/components/ui/switch";

export const Route = createFileRoute("/settings")({
  head: () => ({
    meta: [
      { title: "Configurações — FreightBid" },
      { name: "description", content: "Preferências de perfil e notificações." },
    ],
  }),
  component: SettingsPage,
});

const NOTIF_KEY = "freightbid:notifications";

type NotifPrefs = {
  newAuction: boolean;
  bidSurpassed: boolean;
  closingSoon: boolean;
  auctionResult: boolean;
};

const DEFAULT_PREFS: NotifPrefs = {
  newAuction: true,
  bidSurpassed: true,
  closingSoon: true,
  auctionResult: true,
};

function SettingsPage() {
  return (
    <AppShell>
      <SettingsContent />
    </AppShell>
  );
}

function SettingsContent() {
  const user = useStore((s) => s.user)!;
  const updateProfile = useStore((s) => s.updateProfile);

  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [prefs, setPrefs] = useState<NotifPrefs>(DEFAULT_PREFS);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(NOTIF_KEY);
      if (raw) setPrefs({ ...DEFAULT_PREFS, ...JSON.parse(raw) });
    } catch {}
  }, []);

  const togglePref = (key: keyof NotifPrefs) => {
    setPrefs((p) => {
      const next = { ...p, [key]: !p[key] };
      try {
        localStorage.setItem(NOTIF_KEY, JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  const saveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !email.trim()) {
      toast.error("Preencha nome e e-mail");
      return;
    }
    updateProfile({ name: name.trim(), email: email.trim() });
    toast.success("Perfil atualizado");
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Configurações</h1>
        <p className="text-sm text-muted-foreground">Gerencie seu perfil e preferências de notificação.</p>
      </div>

      <section className="rounded-xl border border-border bg-[var(--surface)] p-6">
        <header className="mb-5 flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/15 text-primary">
            <UserIcon className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-base font-semibold">Perfil</h2>
            <p className="text-xs text-muted-foreground">Atualize seu nome de exibição e e-mail.</p>
          </div>
        </header>
        <form onSubmit={saveProfile} className="space-y-4">
          <div>
            <label className="text-xs font-medium text-muted-foreground">Nome de exibição</label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground">E-mail</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
            />
          </div>
          <button
            type="submit"
            className="rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
          >
            Salvar
          </button>
        </form>
      </section>

      <section className="rounded-xl border border-border bg-[var(--surface)] p-6">
        <header className="mb-5 flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/15 text-primary">
            <Bell className="h-4 w-4" />
          </div>
          <div>
            <h2 className="text-base font-semibold">Notificações</h2>
            <p className="text-xs text-muted-foreground">Escolha quais alertas deseja receber.</p>
          </div>
        </header>
        <div className="divide-y divide-border">
          <ToggleRow
            label="Novo leilão disponível"
            description="Avisar quando um novo leilão for aberto."
            checked={prefs.newAuction}
            onChange={() => togglePref("newAuction")}
          />
          <ToggleRow
            label="Lance superado"
            description="Avisar quando outra transportadora der um lance menor que o seu."
            checked={prefs.bidSurpassed}
            onChange={() => togglePref("bidSurpassed")}
          />
          <ToggleRow
            label="Leilão encerrando em menos de 5 minutos"
            description="Aviso urgente nos minutos finais."
            checked={prefs.closingSoon}
            onChange={() => togglePref("closingSoon")}
          />
          <ToggleRow
            label="Resultado de leilão"
            description="Avisar quando um leilão for encerrado (vitória ou derrota)."
            checked={prefs.auctionResult}
            onChange={() => togglePref("auctionResult")}
          />
        </div>
      </section>
    </div>
  );
}

function ToggleRow({
  label, description, checked, onChange,
}: { label: string; description: string; checked: boolean; onChange: () => void }) {
  return (
    <div className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0">
      <div className="min-w-0">
        <div className="text-sm font-medium">{label}</div>
        <div className="text-xs text-muted-foreground">{description}</div>
      </div>
      <Switch checked={checked} onCheckedChange={onChange} />

    </div>
  );
}
