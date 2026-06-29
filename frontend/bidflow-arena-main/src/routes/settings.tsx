import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { AlertTriangle, Bell, CheckCircle2, Loader2, User as UserIcon } from "lucide-react";
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
  const [prefs, setPrefs] = useState<NotifPrefs>(DEFAULT_PREFS);
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const normalizedName = name.trim();
  const profileIsValid = normalizedName.length > 0;
  const hasProfileChanges = normalizedName !== user.name;

  useEffect(() => {
    if (!savingProfile && !hasProfileChanges) {
      setName(user.name);
    }
  }, [hasProfileChanges, savingProfile, user.name]);

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

  const submitProfile = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!profileIsValid) {
      const message = "Preencha o nome de exibição";
      setProfileError(message);
      toast.error(message);
      return;
    }

    if (!hasProfileChanges) return;

    setSavingProfile(true);
    setProfileError(null);

    try {
      await updateProfile({ name: normalizedName });
      setSavedAt(new Date());
      toast.success("Perfil atualizado");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Erro ao atualizar perfil";
      setProfileError(message);
      toast.error(message);
    } finally {
      setSavingProfile(false);
    }
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
            <p className="text-xs text-muted-foreground">
              Atualize seu nome de exibição. O e-mail é apenas informativo.
            </p>
          </div>
        </header>
        <form onSubmit={submitProfile} className="space-y-4">
          <div>
            <label className="text-xs font-medium text-muted-foreground">Nome de exibição</label>
            <input
              value={name}
              onChange={(e) => {
                setProfileError(null);
                setSavedAt(null);
                setName(e.target.value);
              }}
              className="mt-1 h-10 w-full rounded-md border border-border bg-background px-3 text-sm focus:border-primary focus:outline-none"
            />
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground">E-mail</label>
            <input
              type="email"
              value={user.email}
              disabled
              className="mt-1 h-10 w-full cursor-not-allowed rounded-md border border-border bg-background/60 px-3 text-sm text-muted-foreground focus:outline-none"
            />
            <div className="mt-1 text-[11px] text-muted-foreground">
              O e-mail da conta não pode ser alterado pelo perfil.
            </div>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <ProfileSaveStatus
              saving={savingProfile}
              error={profileError}
              savedAt={savedAt}
              hasChanges={hasProfileChanges}
              isValid={profileIsValid}
            />
            <button
              type="submit"
              disabled={!hasProfileChanges || !profileIsValid || savingProfile}
              className="inline-flex items-center justify-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {savingProfile && <Loader2 className="h-4 w-4 animate-spin" />}
              Salvar
            </button>
          </div>
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
            label="Novo menor lance"
            description="Avisar a cada novo menor lance registrado; se você era líder, o aviso informa que seu lance foi superado."
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
            description="Avisar quando um leilão for encerrado e mostrar a transportadora campeã."
            checked={prefs.auctionResult}
            onChange={() => togglePref("auctionResult")}
          />
        </div>
      </section>
    </div>
  );
}

function ProfileSaveStatus({
  saving,
  error,
  savedAt,
  hasChanges,
  isValid,
}: {
  saving: boolean;
  error: string | null;
  savedAt: Date | null;
  hasChanges: boolean;
  isValid: boolean;
}) {
  if (saving) {
    return (
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
        Salvando perfil...
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 text-xs text-destructive">
        <AlertTriangle className="h-3.5 w-3.5" />
        {error}
      </div>
    );
  }

  if (hasChanges && !isValid) {
    return (
      <div className="flex items-center gap-2 text-xs text-warning">
        <AlertTriangle className="h-3.5 w-3.5" />
        Preencha o nome para salvar.
      </div>
    );
  }

  if (hasChanges) {
    return <div className="text-xs text-muted-foreground">Alteração pendente. Clique em Salvar.</div>;
  }

  if (savedAt) {
    return (
      <div className="flex items-center gap-2 text-xs text-success">
        <CheckCircle2 className="h-3.5 w-3.5" />
        Salvo às {savedAt.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
      </div>
    );
  }

  return <div className="text-xs text-muted-foreground">Perfil sincronizado com a conta.</div>;
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
