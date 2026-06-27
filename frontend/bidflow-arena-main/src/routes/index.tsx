import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { useStore } from "@/lib/store";

export const Route = createFileRoute("/")({
  component: IndexRedirect,
});

function IndexRedirect() {
  const navigate = useNavigate();
  const user = useStore((s) => s.user);
  useEffect(() => {
    if (user?.role === "ADMIN") navigate({ to: "/admin" });
    else if (user) navigate({ to: "/dashboard" });
    else navigate({ to: "/login" });
  }, [user, navigate]);
  return (
    <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground text-sm">
      Redirecionando...
    </div>
  );
}
